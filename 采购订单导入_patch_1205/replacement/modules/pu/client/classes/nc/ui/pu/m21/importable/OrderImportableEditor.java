package nc.ui.pu.m21.importable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ibm.db2.jcc.a.c;
import com.ibm.db2.jcc.am.co;
import com.ibm.db2.jcc.am.i;

import nc.ui.pub.beans.UIRefPane;
import nc.ui.pub.bill.BillData;
import nc.ui.pub.bill.BillItem;
import nc.ui.pub.bill.IBillItem;
import nc.ui.pubapp.uif2app.view.ShowUpableBillForm;
import nc.ui.so.mreturnassign.model.returnassign_base_config;
import nc.ui.trade.excelimport.InputItem;
import nc.ui.trade.excelimport.InputItemCreator;
import nc.ui.uif2.editor.IBillCardPanelEditor;
import nc.ui.uif2.excelimport.DefaultUIF2ImportableEditor;
import nc.util.mmf.framework.base.MMValueCheck;
import nc.vo.bd.bom.bom0202.message.MMBDLangConstBom0202;
import nc.vo.bill.pub.BillUtil;
import nc.vo.pu.m21.entity.OrderHeaderVO;
import nc.vo.pu.m21.entity.OrderItemVO;
import nc.vo.pub.CircularlyAccessibleValueObject;
import nc.vo.pub.ExtendedAggregatedValueObject;
import nc.vo.pubapp.pattern.exception.ExceptionUtils;

public class OrderImportableEditor extends DefaultUIF2ImportableEditor {
	
//	共享页签
	private static List<String> shareTab = Arrays.asList("arrival", "policy", "execute");
//	表体中的必填项
	private static List<String> needImport = Arrays.asList(
			OrderItemVO.PK_SRCMATERIAL, OrderItemVO.PK_ARRVSTOORG, OrderItemVO.PK_ARRVSTOORG_V, OrderItemVO.PK_PSFINANCEORG, OrderItemVO.PK_APFINANCEORG,
			OrderItemVO.NMNY, OrderItemVO.NNETPRICE, OrderItemVO.NORIGMNY, OrderItemVO.NORIGNETPRICE, OrderItemVO.NORIGTAXMNY, OrderItemVO.NORIGTAXNETPRICE,
			OrderItemVO.NQTNETPRICE, OrderItemVO.NQTORIGNETPRICE, OrderItemVO.NQTORIGTAXNETPRC, OrderItemVO.NQTTAXNETPRICE, OrderItemVO.NTAXMNY,
			OrderItemVO.NTAXNETPRICE, OrderItemVO.NCALCOSTMNY,
			OrderItemVO.NEXCHANGERATE, OrderItemVO.NNUM, OrderItemVO.NORIGTAXPRICE, OrderItemVO.NORIGPRICE, OrderItemVO.CCURRENCYID, OrderItemVO.CASTUNITID,
			OrderItemVO.CUNITID, OrderItemVO.CQTUNITID, OrderItemVO.VCHANGERATE, OrderItemVO.VQTUNITRATE, OrderItemVO.CSENDCOUNTRYID,
			OrderItemVO.CRECECOUNTRYID, OrderItemVO.CTAXCOUNTRYID, OrderItemVO.FBUYSELLFLAG, OrderItemVO.CTAXCODEID);
	
//	表体中不能导入的项(需要由公式控制的项)
	private static List<String> notImport = Arrays.asList(OrderItemVO.NASTNUM, OrderItemVO.NQTUNITNUM);
	
	@Override
	protected void setProcessedVO(ExtendedAggregatedValueObject eavo) {

		this.setPkOrg4AggImport(eavo, this.getBillcardPanelEditor());
		super.setProcessedVO(eavo);

	}

	/**
	 * 导入时，设置主组织
	 * 
	 * @param eavo
	 * @param editor
	 */
	public void setPkOrg4AggImport(ExtendedAggregatedValueObject eavo,
			IBillCardPanelEditor editor) {
		// editor.getBillCardPanel().addNew();
		CircularlyAccessibleValueObject sourceVO = eavo.getParentVO();// 数据来源
		String blurValue = "";// 组织版本导入的数据，编码字段。
		if (MMValueCheck.isNotEmpty(sourceVO.getAttributeValue("pk_org"))) {// 导入的组织不为null时进行赋值
			blurValue = sourceVO.getAttributeValue("pk_org").toString();
		} else {
			ExceptionUtils.wrappBusinessException(MMBDLangConstBom0202
					.getPK_ORG_NOT_NULL());
		}

		BillData bd = editor.getBillCardPanel().getBillData();
		BillItem item = bd.getHeadItem("pk_org_v");// 组织版本
		UIRefPane refPane = (UIRefPane) item.getComponent();// 组织版本参照
		if (refPane.getRefModel() != null) {
			((ShowUpableBillForm) editor).getBillOrgPanel().setPkOrg(null);
			refPane.getRefModel().clearData();// 清空参照值
			refPane.setBlurValue(blurValue);// 设置新值
			((ShowUpableBillForm) editor).getBillOrgPanel().setPkOrg(
					refPane.getRefPK());
		}
	}

	public List<InputItem> getInputItems() {
		List<InputItem> result = new ArrayList<InputItem>();
		BillData bd = getBillcardPanelEditor().getBillCardPanel().getBillData();
		// 设置表头组织
		BillItem orgItem = bd.getHeadItem(OrderHeaderVO.PK_ORG);
		orgItem.setShow(true);
		orgItem.setEdit(true);
		orgItem.setNull(true);
		
//		保存验证中表体必填项
		setNeedImport(bd);
		
//		导入需由公式控制的项
		setNotImport(bd);
		
		result.addAll(getInputItemForSinglePosition(bd,IBillItem.HEAD));
		result.addAll(getInputItemForSinglePosition(bd,IBillItem.BODY));
		result.addAll(getInputItemForSinglePosition(bd,IBillItem.TAIL));
		return result;
	}
	
	private void setNeedImport(BillData bd){
		for(String att : needImport){
			BillItem item = bd.getBodyItem("material", att);
			item.setShow(true);
			item.setEdit(true);
			item.setNull(true);
		}
	}
	
	private void setNotImport(BillData bd) {
		for(String att : notImport){
			BillItem item = bd.getBodyItem("material", att);
			item.setShow(false);
			item.setEdit(false);
			item.setNull(false);
		}
	}
	
	private static List<InputItem> getInputItemForSinglePosition(BillData bd, int pos) {
		List<InputItem> result = new ArrayList<InputItem>();
		String[] tabcodes = bd.getTableCodes(pos);
		Map<String, BillItem[]> tabItems = new HashMap<>();
		for (int tab = 0; tab < (tabcodes == null ? 0 : tabcodes.length); tab++)
		{
			if(tabcodes[tab].equals("pu_order_payment")){
				continue;
			}
			BillItem[] items = bd.getShowItems(pos, tabcodes[tab]);
			tabItems.put(tabcodes[tab], items);
			String tabname = bd.getTableName(pos, tabcodes[tab]);
			result.addAll(billItems2InputItems(items, tabname));
		}
//		设置共享页签值
		if(pos == IBillItem.BODY){
			for(String code : tabcodes){
				if(null != tabItems.get(code)){
					if(shareTab.contains(code)){
						BillUtil.convertBillItemsToTableCode(tabItems.get(code), code);
					}else{
						BillUtil.convertBillItemsToTableCode(tabItems.get(code), null);
					}
				}
			}
//			去重
			Map<String, List<String>> attMap = new HashMap<>();
			List<InputItem> fin_result = new ArrayList<InputItem>();
			for(InputItem item : result){
				if(attMap.containsKey(item.getTabCode())){
					if(attMap.get(item.getTabCode()).contains(item.getItemKey())){
						continue;
					}
				}else{
					attMap.put(item.getTabCode(), new ArrayList<String>());
				}
				fin_result.add(item);
				List<String> atts = attMap.get(item.getTabCode());
				atts.add(item.getItemKey());
				attMap.put(item.getTabCode(), atts);
			}
			return fin_result;
		}
		return result;
	}
	
	private static List<InputItem> billItems2InputItems(BillItem[] items, String tabname) {
		List<InputItem> result = new ArrayList<InputItem>();
		for (int i = 0; i < (items == null ? 0 : items.length); i++) {
			BillItem item = items[i];
			item.setTableName(tabname);
			if (isImportable(item)) {
				result.add(new BillItemValue(item));
			}
		}
		return result;
	}
	
	private static boolean isImportable(BillItem item) {
		return item.getDataType() != BillItem.IMAGE
				&& item.getDataType() != BillItem.BLANK
				&& item.getDataType() != BillItem.OBJECT;
	}
	
	static class BillItemValue implements InputItem{

		private BillItem item = null;
		
		private Boolean isNotNull = null;
		
		public BillItemValue(BillItem item) {
			super();
			this.item = item;
		}
 
		public String getItemKey() {
			
			return item.getKey();
		}

		public Integer getOrder() {
			return item.getReadOrder();
		}

		public Integer getPos() {
			return item.getPos();
		}

		public String getShowName() {
			return item.getName();
		}

		public String getTabCode() {
			return item.getTableCode();
		}

		public String getTabName() {
			return item.getTableName();
		}

		public boolean isEdit() {
			return item.isEdit();
		}

		public boolean isNotNull() {
			if(this.isNotNull == null)
				return item.isNull();
			else
				return this.isNotNull.booleanValue();
		}
		
		public void setIsNotNull(boolean isNotNull)
		{
			this.isNotNull = new Boolean(isNotNull);
		}

		public boolean isShow() {
			return item.isShow();
		}

		public boolean isMultiLang() {
			return item.getDataType() == BillItem.MULTILANGTEXT;
		}
		
		public BillItem getItem(){
			return item;
		}
	}
}

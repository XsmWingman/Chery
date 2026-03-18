package nc.impl.pfxx.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nc.bs.pfxx.ISwapContext;
import nc.bs.pfxx.plugin.AbstractPfxxPlugin;
import nc.impl.fa.predevaluate.PredevaluateImportImpl;
import nc.itf.fa.prv.IPredevaluateImport;
import nc.itf.fa.service.IAssetService;
import nc.itf.fa.service.IReduceImport;
import nc.pub.billcode.itf.IBillcodeManage;
import nc.pub.fa.card.AssetFieldConst;
import nc.pub.fa.common.consts.BillTypeConst;
import nc.pub.fa.common.manager.VOManager;
import nc.pub.fa.common.util.StringUtils;
import nc.vo.am.common.util.ExceptionUtils;
import nc.vo.am.common.util.StringTools;
import nc.vo.am.proxy.AMProxy;
import nc.vo.fa.asset.AssetVO;
import nc.vo.fa.predevaluate.PredevaluateBodyVO;
import nc.vo.fa.predevaluate.PredevaluateVO;
import nc.vo.fa.predevaluate.PredevaluateHeadVO;
import nc.vo.fa.reduce.ReduceBodyVO;
import nc.vo.fa.reduce.ReduceHeadVO;
import nc.vo.fa.reduce.ReduceVO;
import nc.vo.pfxx.auxiliary.AggxsysregisterVO;
import nc.vo.pfxx.util.PfxxPluginUtils;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFBoolean;

public class PredevaluatePlugin extends AbstractPfxxPlugin {

	@Override
	protected Object processBill(Object vo, ISwapContext swapContext,
			AggxsysregisterVO aggvo) throws BusinessException {
//		获取翻译后的单据信息
		PredevaluateVO billVO = (PredevaluateVO )vo;
		
		// 设置新的单据号
        // 调用单据号接口单据号
        // 获取单据号管理服务
        IBillcodeManage billCodeManager = AMProxy.lookup(IBillcodeManage.class);
        // 取得新的单据号
        String newBill_code =
                billCodeManager.getPreBillCode_RequiresNew(BillTypeConst.PREDEVALUATE,
                        ((PredevaluateHeadVO) billVO.getParent()).getPk_group(),
                        ((PredevaluateHeadVO) billVO.getParent()).getPk_org());
        ((PredevaluateHeadVO) billVO.getParent()).setBill_code(newBill_code);
        
        PredevaluateHeadVO headVO = (PredevaluateHeadVO) billVO.getParentVO();
        // 单据保存或者更新之前调用的查询操作，查询当前单据以前是否被导入过
        // 如果已经被导入过，那么返回已经导入单据的PK，否则返回NULL值
        String billPK =
                PfxxPluginUtils.queryBillPKBeforeSaveOrUpdate(swapContext.getBilltype(), swapContext.getDocID(),
                        swapContext.getOrgPk());
        
     // 如果导入过
        if (null != billPK && billPK.length() > 0) {
            // 查看配置文件信息是否允许导入重复数据
            if (swapContext.getReplace().equalsIgnoreCase("N")) {
                throw new BusinessException(nc.vo.ml.NCLangRes4VoTransl.getNCLangRes().getStrByID("farule_0",
                        "02012060-0037")/*
                                         * @res
                                         * "不允许重复导入单据，如果想更新已导入单据，请把数据文件的replace标志设为‘Y’！"
                                         */);
            }
            // 设置主键
            headVO.setPrimaryKey(billPK);
            // 查询数据库中是否存在该单据
            PredevaluateVO reduceVO = AMProxy.lookup(IPredevaluateImport.class).queryPredevaluateVOByPk(billPK);
            if (null != reduceVO) {
                throw new BusinessException(nc.vo.ml.NCLangRes4VoTransl.getNCLangRes().getStrByID("farule_0",
                        "02012060-0038")/* @res "该单据已经导入，并且已经审核，不允许重复导入。" */);
            }
        }
        
//        查询卡片
        AssetVO[] cardVos = queryCardVOs(billVO);
        
//        设置卡片值
        setBillVOValue(billVO, cardVos);
        
     // 插入资产减值单
        PredevaluateVO reduceVO = AMProxy.lookup(IPredevaluateImport.class).insertPredevaluateVO(null, billVO);
        
        return reduceVO;
	}
	
	private void setBillVOValue(PredevaluateVO billVO, AssetVO[] cardVOs) {
	    
        Map<String, AssetVO> cardsMap = new HashMap<String, AssetVO>();
        for (AssetVO cardvo : cardVOs) {
            cardsMap.put(cardvo.getPk_card(), cardvo);
        }
        
        PredevaluateBodyVO[] bodyVOs = (PredevaluateBodyVO[]) billVO.getChildrenVO();
        for (PredevaluateBodyVO bodyVO : bodyVOs) {
            AssetVO cardVO = cardsMap.get(bodyVO.getPk_card());
//            本币原值
            bodyVO.setLocaloriginvalue(cardVO.getLocaloriginvalue());
//            累计折旧
            bodyVO.setAccudep(cardVO.getAccudep());
            // 币种
            bodyVO.setPk_currency(cardVO.getPk_currency());
        }
        
    }

	 /**
     * 
     * 查询卡片
     * <p>
     * <b>examples:</b>
     * <p>
     * 使用示例
     * <p>
     * <b>参数说明</b>
     * 
     * @param billVO
     * @param showKeys
     * @return
     * @throws BusinessException
     *             <p>
     * @author weizq
     * @time 2011-4-19 下午07:00:08
     */
    private AssetVO[] queryCardVOs(PredevaluateVO billVO) throws BusinessException {
    
        String[] pk_cards = VOManager.getAttributeValueArray(billVO.getChildrenVO(), AssetFieldConst.PK_CARD);
        
        PredevaluateHeadVO headVO = (PredevaluateHeadVO) billVO.getParentVO();
        String whereSQL =
                " fa_card.pk_card in (" + StringTools.buildStringUseSpliterWithQuotes(pk_cards, ",") + ") "
                        + " and laststate_flag = 'Y' ";
        if (StringUtils.isNotEmpty(headVO.getPk_accbook())) {
            whereSQL += " and pk_accbook = '" + headVO.getPk_accbook() + "'";
        } else {
            whereSQL += " and business_flag = '" + UFBoolean.TRUE + "'";
        }
        
        // 添加查询字段
        List<String> keysList = new ArrayList<String>();
        keysList.add(AssetFieldConst.PK_CARD);
        keysList.add(AssetFieldConst.LOCALORIGINVALUE);
        keysList.add(AssetFieldConst.ACCUDEP);
        keysList.add(AssetFieldConst.PK_CURRENCY);
        
        AssetVO[] assetVOs =
                AMProxy.lookup(IAssetService.class).queryAssetFieldValues(whereSQL, keysList.toArray(new String[0]));
        
        if (assetVOs == null || assetVOs.length == 0) {
            ExceptionUtils.asBusinessException(nc.vo.ml.NCLangRes4VoTransl.getNCLangRes().getStrByID("farule_0",
                    "02012060-0041")/* @res " 没有查询到任何一张卡片, 请检查 \n " */);
        }
        
        return assetVOs;
    }
}

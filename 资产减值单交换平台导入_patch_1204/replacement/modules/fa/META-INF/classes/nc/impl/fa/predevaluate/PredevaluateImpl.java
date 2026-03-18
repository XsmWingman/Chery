package nc.impl.fa.predevaluate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nc.bs.am.framework.action.IActionTemplate;
import nc.bs.am.framework.action.IRule;
import nc.bs.am.framework.action.approve.ApproveActionTemplate;
import nc.bs.am.framework.action.approve.UnApproveActionTemplate;
import nc.bs.am.framework.common.rule.ValidateServiceRule;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.uif2.validation.Validator;
import nc.impl.am.bill.BillBaseDAO;
import nc.impl.am.bill.rule.BillCodeDeleteRule;
import nc.impl.am.bill.rule.BillCodeInsertAfterRule;
import nc.impl.am.bill.rule.BillCodeInsertBeforeRule;
import nc.impl.am.bill.rule.BillCodeUpdateRule;
import nc.impl.am.common.InSqlManager;
import nc.impl.am.db.QueryUtil;
import nc.impl.fa.common.rule.CreateBillLogAfterRule;
import nc.impl.fa.common.rule.DeleteBillLogAfterRule;
import nc.impl.fa.common.rule.DeleteFipAfterRule;
import nc.impl.fa.common.rule.UpdateCardLogAfterRule;
import nc.impl.fa.common.validator.CheckCardLegalValidator;
import nc.impl.fa.common.validator.CheckHasFipMakeVoucherForAccbook;
import nc.impl.fa.common.validator.CheckMinUnClosedBookPeriodValidator;
import nc.impl.fa.predevaluate.rule.CheckCurrentresumeValidator;
import nc.impl.fa.predevaluate.rule.CheckSameAccbookValidator;
import nc.impl.fa.predevaluate.rule.CreateHeadQuarterAssetBillRule;
import nc.impl.fa.predevaluate.rule.DeleteHeadquarterDataFromContextAfterRule;
import nc.impl.fa.predevaluate.rule.HeadquarterCreateBillLogAfterRule;
import nc.impl.fa.predevaluate.rule.HeadquarterDeleteBillLogAfterRule;
import nc.impl.fa.predevaluate.rule.HeadquarterDeleteFipAfterRule;
import nc.impl.fa.predevaluate.rule.HeadquarterSendFipAfterRule;
import nc.impl.fa.predevaluate.rule.HeadquarterUpdateCardLogAfterRule;
import nc.impl.fa.predevaluate.rule.HeadquarterUpdateDepLogAfterRule;
import nc.impl.fa.predevaluate.rule.HeadquarterWriteBackToCardhistoryAfterRule;
import nc.impl.fa.predevaluate.rule.PredevaluateSendFipAfterRule;
import nc.impl.fa.predevaluate.rule.PredevaluateWriteBackToCardhistoryAfterRule;
import nc.impl.fa.predevaluate.rule.UnApproveHeadQuarterAssetBillRule;
import nc.impl.fa.predevaluate.rule.UpdateDepLogAfterRuleForPredevaluate;
import nc.impl.fa.rule.FAAccbookCheckSealRule;
import nc.impl.fa.rule.ReceiptScanBeforeRule;
import nc.itf.fa.prv.IPredevaluate;
import nc.itf.fa.service.IAccbookInfoService;
import nc.itf.fa.service.IAssetGroupService;
import nc.itf.fa.service.IAssetService;
import nc.itf.fa.service.ICloseBookService;
import nc.itf.fa.service.ILogService;
import nc.pub.fa.asset.manager.AccbookChangeUtil;
import nc.pub.fa.card.AssetFieldConst;
import nc.pub.fa.card.AssetQuerySqlTool;
import nc.pub.fa.common.consts.LogTypeConst;
import nc.pub.fa.common.manager.VOManager;
import nc.pub.fa.common.util.StringUtils;
import nc.vo.am.common.BaseLockData;
import nc.vo.am.common.TransportBillVO;
import nc.vo.am.common.util.ArrayUtils;
import nc.vo.am.common.util.BaseVOUtils;
import nc.vo.am.common.util.BillTransportTool;
import nc.vo.am.common.util.ExceptionUtils;
import nc.vo.am.constant.BillStatusConst;
import nc.vo.am.manager.AccbookManager;
import nc.vo.am.manager.AccperiodVO;
import nc.vo.am.manager.CurrencyRateManager;
import nc.vo.am.manager.LockManager;
import nc.vo.am.proxy.AMProxy;
import nc.vo.fa.accbookinfo.AccbookBodyVO;
import nc.vo.fa.asset.AssetVO;
import nc.vo.fa.assetgroup.AssetGroupHeadVO;
import nc.vo.fa.predevaluate.PredevaluateBodyVO;
import nc.vo.fa.predevaluate.PredevaluateHeadVO;
import nc.vo.fa.predevaluate.PredevaluateVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFDate;
import nc.vo.pub.lang.UFDouble;
import nc.vo.uif2.LoginContext;

/**
 * <p>
 * 固定资产减值单的内部接口的实现类。<br>
 * 实现了以下方法接口：
 * </p>
 * <u>
 *     <li>增加
 *     <li>删除
 *     <li>修改
 *     <li>审批
 *     <li>弃审
 *     <li>提交
 *     <li>收回
 * </u>
 *
 * @author qicong
 * @version 6.0
 * @see BillBaseDAO
 * @see IPredevaluate
 */
public class PredevaluateImpl extends BillBaseDAO<PredevaluateVO>
implements IPredevaluate {



	/**
	 * <p>
	 * 新增固定资产减值单。
	 * </p>
	 *
	 * @param context
	 *            登陆信息
	 * @param vo
	 *            固定资产减值单聚合VO
	 * @return TransportBillVO
	 *             流量优化的固定资产减值单聚合VO
	 * @throws BusinessException
	 *             业务异常
	 */
	@Override
	public TransportBillVO insert(LoginContext context, PredevaluateVO vo)
			throws BusinessException {
		// 校验减值单信息。
		validateBillForInsertion(context, vo);

		// 处理多币种。
		dealMultiCurrency(vo);

		// 新增固定资产减值单。
		PredevaluateVO billVO = insertBillVO(vo)[0];

		// 更新减值单中卡片对应的fa_log信息：增加减值单的fa_log信息。
		updateLog(vo, null, false);

		// 取得当前单据的总部资产。
		String headQuartersPkAssetGroup = ((PredevaluateHeadVO) vo
				.getParentVO()).getPk_assetgroup_headquarters();
		// 如果有总部资产，则更新fa_log中的总部资产卡片信息。
		if (StringUtils.isNotEmpty(headQuartersPkAssetGroup)) {
			String headQuartersAccbook = getHeadAssetGroupAccbook((PredevaluateHeadVO)vo.getParentVO());
			String headQuartersPkOrg = getHeadAssetGroupPK((PredevaluateHeadVO)vo.getParentVO());


			// 取得当前总部资产下的卡片。
			AssetVO[] assetVOs = getAssetCardService().queryAssetVOByAssetGroup(
					headQuartersPkOrg, headQuartersPkAssetGroup, headQuartersAccbook);
			// 取得总部资产下的卡片主键。
			String[] cardsByHeadquarters = VOManager.getAttributeValueArray(
					assetVOs, PredevaluateBodyVO.PK_CARD);
			// 更新总部资产卡片对应的fa_log信息。
			updateLog(vo, cardsByHeadquarters, false);
		}

		// 解锁卡片。
		releasePKCardLock(vo);

		// 减少流量处理。前台没有变化的数据不再重复传递；
		// 只向前台返回变化的数据：pk,ts,creator,creationtime...
		TransportBillVO transportBillVO =
				BillTransportTool.createTransportBill(billVO, BillTransportTool.MODE_INSERT);

		return transportBillVO;
	}
	
	/**
	 * <p>
	 * 新增固定资产减值单-不使用流量处理
	 * </p>
	 *
	 * @param context
	 *            登陆信息
	 * @param vo
	 *            固定资产减值单聚合VO
	 * @return TransportBillVO
	 *             流量优化的固定资产减值单聚合VO
	 * @throws BusinessException
	 *             业务异常
	 */
	public PredevaluateVO insertPredevaluateVO(LoginContext context, PredevaluateVO vo)
			throws BusinessException {
		// 校验减值单信息。
		validateBillForInsertion(context, vo);

		// 处理多币种。
		dealMultiCurrency(vo);

		// 新增固定资产减值单。
		PredevaluateVO billVO = insertBillVO(vo)[0];

		// 更新减值单中卡片对应的fa_log信息：增加减值单的fa_log信息。
		updateLog(vo, null, false);

		// 取得当前单据的总部资产。
		String headQuartersPkAssetGroup = ((PredevaluateHeadVO) vo
				.getParentVO()).getPk_assetgroup_headquarters();
		// 如果有总部资产，则更新fa_log中的总部资产卡片信息。
		if (StringUtils.isNotEmpty(headQuartersPkAssetGroup)) {
			String headQuartersAccbook = getHeadAssetGroupAccbook((PredevaluateHeadVO)vo.getParentVO());
			String headQuartersPkOrg = getHeadAssetGroupPK((PredevaluateHeadVO)vo.getParentVO());


			// 取得当前总部资产下的卡片。
			AssetVO[] assetVOs = getAssetCardService().queryAssetVOByAssetGroup(
					headQuartersPkOrg, headQuartersPkAssetGroup, headQuartersAccbook);
			// 取得总部资产下的卡片主键。
			String[] cardsByHeadquarters = VOManager.getAttributeValueArray(
					assetVOs, PredevaluateBodyVO.PK_CARD);
			// 更新总部资产卡片对应的fa_log信息。
			updateLog(vo, cardsByHeadquarters, false);
		}

		// 解锁卡片。
		releasePKCardLock(vo);

		return billVO;
	}

	/**
	 * 设置保存前后规则
	 */
	@Override
	protected void initInsertActionRules(IActionTemplate<PredevaluateVO> insertAction) {
		// 调用父类方法。
		super.initInsertActionRules(insertAction);

		// 初始化新增校验类列表。
		List<Validator> validators = new ArrayList<Validator>();
		//添加是否启用账簿的校验
		validators.add(new FAAccbookCheckSealRule());
		// 添加资产卡片是否合法的校验。
		validators.add(new CheckCardLegalValidator());
		// 添加单据的当前业务日期是否为最小未结账月的校验。
		validators.add(new CheckMinUnClosedBookPeriodValidator());
		// 添加判断财务是否已经制成折旧清单的校验。
		validators.add(new CheckHasFipMakeVoucherForAccbook());
		// 添加总部资产减值和资产组减值是否是同一个(基准)账簿的校验。
		validators.add(new CheckSameAccbookValidator());
		insertAction.addBeforeRule(new ValidateServiceRule<PredevaluateVO>(validators));

		// 添加单据编码新增前处理规则。
		insertAction.addBeforeRule(new BillCodeInsertBeforeRule<PredevaluateVO>());
		// 添加单据编码新增后处理规则。
		insertAction.addAfterRule(new BillCodeInsertAfterRule<PredevaluateVO>());
		// 添加更新资产卡片日志的规则。
		insertAction.addAfterRule(new UpdateCardLogAfterRule<PredevaluateVO>(false));
	}

	/**
	 * 校验资产组和总部资产必须在相同的最小未结账月
	 * @param orgHeadVO 资产组的信息
	 * @param headQuartersAccbook 总部资产组的账簿
	 * @param headQuartersPkOrg 总部资产组对应的财务组织
	 * @throws BusinessException
	 */
	private void validateOrgAndHeadquartersMinUnCloseBook(
			PredevaluateHeadVO orgHeadVO, String headQuartersAccbook,
			String headQuartersPkOrg) throws BusinessException {

		//资产组最小未结账月
		AccperiodVO orgMinUnCloseBook = AMProxy.lookup(ICloseBookService.class)
				.queryMinUnClosebookPeriod(orgHeadVO.getPk_org(), orgHeadVO.getPk_accbook());

		//总部资产组最小未结账月
		AccperiodVO headQuartersMinUnCloseBook = AMProxy.lookup(ICloseBookService.class)
				.queryMinUnClosebookPeriod(headQuartersPkOrg, headQuartersAccbook);

		//判断资产组的最小未结账月和总部资产组的最小未结账月
		if (!(orgMinUnCloseBook.getAccyear() + orgMinUnCloseBook.getPeriod())
				.equals(headQuartersMinUnCloseBook.getAccyear()+ headQuartersMinUnCloseBook.getPeriod())) {
			ExceptionUtils.asBusinessException(nc.vo.ml.NCLangRes4VoTransl.getNCLangRes().getStrByID("predevaluate_0","02012035-0035")/*@res "总部资产与资产组不在同一最小未结账月。 \n"*/);
		}
	}

	/**
	 * <p>
	 * 新增固定资产减值单前校验减值单信息。
	 * </p>
	 *
	 * @param context
	 *            登陆信息
	 * @param vo
	 *            固定资产减值单聚合VO
	 * @throws BusinessException
	 *             业务异常
	 */
	private void validateBillForInsertion(LoginContext context, PredevaluateVO vo)
			throws BusinessException {
		// 校验有无转回卡片，以及相应卡片是否可以转回。
		checkCanRedevaluate(context, vo);

		// 取得当前单据的总部资产。
		String headQuartersPkAssetGroup = ((PredevaluateHeadVO) vo
				.getParentVO()).getPk_assetgroup_headquarters();
		if (StringUtils.isNotEmpty(headQuartersPkAssetGroup)) {
			String headQuartersAccbook = getHeadAssetGroupAccbook((PredevaluateHeadVO)vo.getParentVO());
			String headQuartersPkOrg = getHeadAssetGroupPK((PredevaluateHeadVO)vo.getParentVO());
			// 校验资产组和总部资产必须在相同的最小未结账月
			validateOrgAndHeadquartersMinUnCloseBook((PredevaluateHeadVO)vo.getParentVO(),
					headQuartersAccbook, headQuartersPkOrg);
		}
		// 校验是否资产组已经做过减值单，而且单据状态是未审批状态。
		validateAssetgroup(vo);
	}

	/**
	 * <p>
	 * 校验有无转回卡片，以及相应卡片是否可以转回。
	 * </p>
	 *
	 * @param context
	 *            登陆信息
	 * @param vo
	 *            固定资产减值单聚合VO
	 * @throws BusinessException
	 *             业务异常
	 */
	private void checkCanRedevaluate(LoginContext context, PredevaluateVO vo)
			throws BusinessException {
		/** 取得表体中所有转回的卡片 */
		// 取得表体VO数组。
		PredevaluateBodyVO[] bodyVOs = (PredevaluateBodyVO[]) vo.getChildrenVO();
		if (ArrayUtils.isNotEmpty(bodyVOs)) {
			// 初始化保存转回卡片的map。
			HashMap<String, Integer> recordPk_cardAndRow = new HashMap<String, Integer>();
			// 遍历表体的固定资产卡片，汇总本次计提为负（转回）的卡片。
			for (int i = 0; i < bodyVOs.length; i++) {
				// 本次记提非空且小于0。
				if (bodyVOs[i].getCurrentresume() != null
						&& bodyVOs[i].getCurrentresume().compareTo(UFDouble.ZERO_DBL) < 0) {
					recordPk_cardAndRow.put(bodyVOs[i].getPk_card(), i);
				}
			}
			// 减值单中含有转回的卡片。
			if (recordPk_cardAndRow.size() != 0) {
				/** 取得所有转回卡片的AssetVO和对应的资产类别 */
				// 查询出表体所有卡片。
				String[] pks = BaseVOUtils.getVOAttributeValues(bodyVOs, PredevaluateBodyVO.PK_CARD);
				// 根据表体卡片查询出其对应的主账簿的AssetVO。
				//                AssetVO[] assetVOs = getAssetCardService().queryMainAssetVOByPks(context, pks);
				StringBuffer whereSql = new StringBuffer();
				whereSql.append("laststate_flag = 'Y' and business_flag = 'Y' and pk_card in")
				.append(InSqlManager.getInSQLValue(pks));
				String adjustWhereSql = AssetQuerySqlTool.adjustWhereSQL(whereSql.toString());
				AssetVO[] assetVOs = getAssetCardService().queryAssetFieldValues(adjustWhereSql, 
						new String[] {AssetFieldConst.PK_CARD, AssetFieldConst.PK_CATEGORY});

				List<String> categorys = new ArrayList<String>();
				// <pk_card, pk_category>
				HashMap<String, String> recordPk_cardAndCategory = new HashMap<String, String>();
				// 取得所有AssetVO的资产类别。
				for (int i = 0; i < assetVOs.length; i++) {
					if (!categorys.contains(assetVOs[i].getPk_category())) {
						categorys.add(assetVOs[i].getPk_category());
					}
					recordPk_cardAndCategory.put(assetVOs[i].getPk_card(), assetVOs[i].getPk_category());
				}

				/** 取得所有不能转回的资产类别 */
				// 取得资产账簿。
				String pk_accbook = ((PredevaluateHeadVO) vo.getParent()).getPk_accbook();
				// 取得财务组织。
				String pk_org = ((PredevaluateHeadVO) vo.getParent()).getPk_org();
				// 取得业务日期。
				UFDate business_date = ((PredevaluateHeadVO) vo.getParent()).getBusiness_date();
				// 取得账簿分类主键。
				String[] pk_accbooks = AccbookChangeUtil.findSetofbookKey(pk_org, new String[] { pk_accbook },
						business_date);
				// 根据账簿分类主键取得账簿分类信息。
				Map<String, AccbookBodyVO> accbookBodys = AMProxy.lookup(IAccbookInfoService.class)
						.queryAccbookinfoBodysByPkCategory(pk_accbooks, categorys,
								((PredevaluateHeadVO) vo.getParent()).getPk_org());
				// 遍历账簿分类信息，记录不能转回的资产类别。
				List<String> recordPk_categoryNotReturn = new ArrayList<String>();
				for (Map.Entry<String, AccbookBodyVO> entry : accbookBodys.entrySet()) {
					if (!entry.getValue().getBcanredevaluate().booleanValue()) {
						recordPk_categoryNotReturn.add(entry.getKey());
					}
				}

				/** 遍历上面的两种信息，判断哪些卡片不能转回。 */
				StringBuffer cardBuffer = new StringBuffer();
				for (int i = 0; i < recordPk_categoryNotReturn.size(); i++) {
					for (Map.Entry<String, String> entry : recordPk_cardAndCategory.entrySet()) {
						String pk_card = entry.getKey();
						String pk_category = entry.getValue();
						if (pk_category.equals(recordPk_categoryNotReturn.get(i))
								&& recordPk_cardAndRow.get(pk_card) != null) {
							int row = recordPk_cardAndRow.get(pk_card) + 1;
							cardBuffer.append(row + ",");
						}
					}
				}
				// 构造错误提示信息。
				if (cardBuffer.toString().trim().length() > 0) {
					StringBuffer errorMsg = new StringBuffer();
					cardBuffer.reverse();
					//errorMsg.append(nc.vo.ml.NCLangRes4VoTransl.getNCLangRes().getStrByID("predevaluate_0","02012035-0036")/*@res "表体第"*/ + cardBuffer.substring(1, cardBuffer.length()) + nc.vo.ml.NCLangRes4VoTransl.getNCLangRes().getStrByID("predevaluate_0","02012035-0037")/*@res "张卡片的资产类别在账簿信息中设置了本期不转回!"*/);
					String row = cardBuffer.substring(1, cardBuffer.length());
					errorMsg.append(nc.vo.ml.NCLangRes4VoTransl.getNCLangRes().getStrByID("predevaluate_0","02012035-0046",null,new String[]{row})/*@res "表体第{0}张卡片的资产类别在账簿信息中设置了本期不转回!"*/);
					ExceptionUtils.asBusinessException(errorMsg.toString());
				}
			}
		}
	}

	/**
	 * <p>
	 * 校验当前资产组是否已经做过减值单，而且单据状态是未审批状态。
	 * </p>
	 *
	 * @param vo 固定资产减值单聚合VO
	 * @throws BusinessException 业务异常
	 */
	private void validateAssetgroup(PredevaluateVO vo) throws BusinessException {
		// 取得表头数据。
		PredevaluateHeadVO headVO = (PredevaluateHeadVO) vo.getParent();

		// 设置校验SQL文。
		String whereCondStr = PredevaluateHeadVO.PK_ASSETGROUP + " = '" +
				headVO.getPk_assetgroup() + "' and dr = 0 and " +
				PredevaluateHeadVO.BILL_STATUS + " <> '" + BillStatusConst.check_pass + "'";
		// 取得当前资产组下的单据。
		PredevaluateVO[] billVOs = QueryUtil.queryBillVOByHeadCond(
				PredevaluateVO.class, whereCondStr, null, false);

		if (ArrayUtils.isNotEmpty(billVOs)) {
			PredevaluateVO billVO = billVOs[0];
			/*ExceptionUtils.asBusinessException(nc.vo.ml.NCLangRes4VoTransl.getNCLangRes().getStrByID("predevaluate_0","02012035-0038")@res "已经做过未审核通过的减值单[" +
                    ((PredevaluateHeadVO)billVO.getParent()).getBill_code()  + nc.vo.ml.NCLangRes4VoTransl.getNCLangRes().getStrByID("predevaluate_0","02012035-0039")@res "]，" +
                    nc.vo.ml.NCLangRes4VoTransl.getNCLangRes().getStrByID("predevaluate_0","02012035-0040")@res "请将其审核，再做该资产组减值。");*/
			String bill_code = ((PredevaluateHeadVO)billVO.getParent()).getBill_code();
			ExceptionUtils.asBusinessException(nc.vo.ml.NCLangRes4VoTransl.getNCLangRes().getStrByID("predevaluate_0","02012035-0047",null,new String[]{bill_code})/*@res "已经做过未审核通过的减值单[{0}]，请将其审核，再做该资产组减值。"*/);
		}
	}

	/**
	 * <p>
	 * 处理单据中的多币种项目，根据账簿设置多币种项目的显示值。
	 * </p>
	 *
	 * @param billVO 减值单聚合VO
	 * @throws BusinessException 业务异常
	 */
	private void dealMultiCurrency(PredevaluateVO billVO) throws BusinessException {
		// 取得表体VO数组。
		PredevaluateBodyVO[] bodyVOs = (PredevaluateBodyVO[]) billVO.getChildrenVO();

		// 表体非空时，处理多币种项目。
		if (ArrayUtils.isNotEmpty(bodyVOs)) {
			// 取得表头数据。
			PredevaluateHeadVO headVO = (PredevaluateHeadVO) billVO.getParentVO();
			// 取得资产账簿。
			String pk_accbook = headVO.getPk_accbook();
			// 当前资产账簿为业务账簿时，根据财务组织取得主账簿。
			if (AccbookManager.isBizAccbook(pk_accbook)) {
				pk_accbook = AccbookManager.queryMainAccbookIDByOrg(headVO.getPk_org());
			}

			// 取得业务日期。
			UFDate date = headVO.getBusiness_date();

			// 表头多币种字段：本次计提。
			String[] headMultiCurrencyField =
					new String[] {PredevaluateHeadVO.CURRENTRESUME};
			// 处理表头的多币种项目。
			CurrencyRateManager.setLocalMoneyByAccbook(
					headVO, headMultiCurrencyField, pk_accbook, date);

			// 表体多币种字段：本次计提。
			String[] bodyMultiCurrencyField =
					new String[]{PredevaluateBodyVO.CURRENTRESUME};
			// 处理表体的多币种项目。
			for (PredevaluateBodyVO bodyVO : bodyVOs) {
				bodyVO.setPk_group(headVO.getPk_group());
				CurrencyRateManager.setLocalMoneyByAccbook(
						bodyVO, bodyMultiCurrencyField, pk_accbook, date);
			}
		}
	}

	/**
	 * <p>
	 * 删除固定资产减值单。
	 * </p>
	 *
	 * @param billVO
	 *            固定资产减值单聚合VO数组
	 * @throws BusinessException
	 *             业务异常
	 */
	@Override
	public void delete(PredevaluateVO billVO)
			throws BusinessException {
		// 根据主键重新查询单据数据，取得没有传递过来的字段。
		PredevaluateVO vo = reQueryBillByPK(billVO.getParent().getPrimaryKey());

		// 更新减值单中卡片对应的fa_log信息：清空单据的fa_log信息。
		updateLog(vo, null, true);

		// 删除减值单。
		deleteBillVO(vo);
	}

	/**
	 * <p>
	 * 删除保存的扩展操作，进行后台单据数据校验逻辑。
	 * </p>
	 *
	 * @see nc.impl.am.bill.BillBaseDAO
	 *              #initDeleteActionRules(nc.impl.am.bill.action.BillDeleteAction)
	 */
	@Override
	protected void initDeleteActionRules(IActionTemplate<PredevaluateVO> deleteAction) {
		// 调用父类方法，补充删除前的vo数据。
		super.initDeleteActionRules(deleteAction);

		/** 以下的卡片合法校验，用于取消审批资产组减值单处理中、自动删除关联的总部资产减值单时，
		 * 校验总部资产减值单的资产卡片是否有后续操作。
		 * */
		// 初始化新增校验类列表。
		List<Validator> validators = new ArrayList<Validator>();
		// 添加资产卡片是否合法的校验。
		validators.add(new CheckCardLegalValidator());
		deleteAction.addBeforeRule(new ValidateServiceRule<PredevaluateVO>(validators));

		// 添加单据号回退的规则。
		deleteAction.addAfterRule(new BillCodeDeleteRule<PredevaluateVO>());
		// 添加更新资产卡片日志的规则。
		deleteAction.addAfterRule(new UpdateCardLogAfterRule<PredevaluateVO>(false, true));
	}

	/**
	 * <p>
	 * 重新查询单据数据，取得没有传递过来的字段。
	 * </p>
	 *
	 * @param pks 单据主键
	 * @return PredevaluateVO 单据数据
	 */
	private PredevaluateVO reQueryBillByPK(String... pks) {
		// 重新查询单据数据。
		PredevaluateVO[] aggVO = QueryUtil.queryBillVOByPks(PredevaluateVO.class, pks, true);

		if (ArrayUtils.isNotEmpty(aggVO)) {
			return aggVO[0];
		} else {
			return null;
		}
	}

	/**
	 * <p>
	 * 修改固定资产减值单。
	 * </p>
	 *
	 * @param context
	 *            登陆信息
	 * @param vo
	 *            固定资产减值单聚合VO
	 * @return TransportBillVO
	 *             固定资产减值单聚合VO
	 * @throws BusinessException
	 *             业务异常
	 */
	@Override
	public TransportBillVO update(LoginContext context, PredevaluateVO vo)
			throws BusinessException {
		// 校验有无转回卡片，以及相应卡片是否可以转回。
		checkCanRedevaluate(context, vo);

		// 处理单据中的多币种项目，根据账簿设置多币种项目的显示值。
		dealMultiCurrency(vo);

		// 加单据锁
		LockManager.lock(new BaseLockData<PredevaluateVO>(vo));

		// 更新减值单。
		PredevaluateVO[] billVOs = updateBillVO(new PredevaluateVO[] {vo}, null);

		if (ArrayUtils.isNotEmpty(billVOs)) {
			PredevaluateVO billVO = billVOs[0];

			// 解锁卡片。
			releasePKCardLock(vo);

			// 减少流量处理。前台没有变化的数据不再重复传递；
			// 只向前台返回变化的数据：pk,ts,modifier,modifiedtime...
			TransportBillVO transportBillVO =
					BillTransportTool.createTransportBill(billVO, BillTransportTool.MODE_UPDATE);

			return transportBillVO;
		} else {
			return null;
		}
	}

	@Override
	protected void initUpdateActionRules(IActionTemplate<PredevaluateVO> updateAction) {

		// 调用父类方法。
		super.initUpdateActionRules(updateAction);

		// 初始化新增校验类列表。
		List<Validator> validators = new ArrayList<Validator>();
		// 添加资产卡片是否合法的校验。
		validators.add(new CheckCardLegalValidator());
		// 添加单据的当前业务日期是否为最小未结账月的校验。
		validators.add(new CheckMinUnClosedBookPeriodValidator());
		// 添加总部资产减值和资产组减值是否是同一个(基准)账簿的校验。
		validators.add(new CheckSameAccbookValidator());
		updateAction.addBeforeRule(new ValidateServiceRule<PredevaluateVO>(validators));

		// 添加单据编码更新处理规则。
		updateAction.addBeforeRule(new BillCodeUpdateRule<PredevaluateVO>());
		// 添加更新资产卡片日志的规则。
		updateAction.addAfterRule(new UpdateCardLogAfterRule<PredevaluateVO>(false, false, true));
	}

	/**
	 * <p>
	 * 提交固定资产减值单。
	 * </p>
	 *
	 * @param context
	 *            登陆信息
	 * @param billVOs
	 *            固定资产减值单聚合VO
	 * @return PredevaluateVO
	 *             固定资产减值单聚合VO
	 * @throws BusinessException
	 *             业务异常
	 */
	public PredevaluateVO commit(PredevaluateVO billVO) throws BusinessException {
		return ArrayUtils.getFirstElem(commitBillVO(billVO));
	}

	/**
	 * <p>
	 * 收回固定资产减值单。
	 * </p>
	 *
	 * @param billVOs
	 *            固定资产减值单聚合VO
	 * @return Object
	 *             固定资产减值单聚合VO
	 * @throws BusinessException
	 *             业务异常
	 */
	@Override
	public PredevaluateVO unSave(PredevaluateVO[] billVOs) throws BusinessException {
		// 父类收回单据方法。
		PredevaluateVO[] returnVOs = unSaveBillVOs(billVOs);
		PredevaluateVO billVO = (ArrayUtils.isEmpty(returnVOs) == true ? null : returnVOs[0]);

		return billVO;
	}

	/**
	 * 初始化提交校验
	 */
	@Override
	protected void initCommitActionRules(IActionTemplate<PredevaluateVO> template){

		//影像扫描
		template.addBeforeRule(new ReceiptScanBeforeRule<PredevaluateVO>());

	}

	/**
	 * <p>
	 * 初始化审批资产减值单的规则（含校验）。
	 * </p>
	 *
	 * @see nc.impl.am.bill.BillBaseDAO
	 *              #initApproveRule(nc.bs.am.framework.action.approve.ApproveActionTemplate)
	 */
	@SuppressWarnings({ "unchecked" })
	@Override
	protected void initApproveRule(ApproveActionTemplate<PredevaluateVO> template) {

		// 初始化新增校验类列表。
		List<Validator> validators = new ArrayList<Validator>();
		//添加是否启用账簿的校验
		validators.add(new FAAccbookCheckSealRule());
		// 添加判断财务是否已经制成折旧清单的校验。
		validators.add(new CheckHasFipMakeVoucherForAccbook());
		// 添加“本次计提”是否为0的校验。
		validators.add(new CheckCurrentresumeValidator());
		//添加是否启用账簿的校验
		validators.add(new FAAccbookCheckSealRule());
		template.addApprovedBeforeRule(new ValidateServiceRule<PredevaluateVO>(validators));

		// 初始化审批后的业务规则。
		List<IRule<PredevaluateVO>> approvePassAfterRules = new ArrayList<IRule<PredevaluateVO>>();
		// 添加回写资产卡片历史表信息的规则。
		approvePassAfterRules.add(new PredevaluateWriteBackToCardhistoryAfterRule(true));
		// 添加新增单据日志的规则。
		approvePassAfterRules.add(new CreateBillLogAfterRule<PredevaluateVO>(LogTypeConst.predevaluate));
		// 添加更新资产卡片日志的规则。
		approvePassAfterRules.add(new UpdateCardLogAfterRule<PredevaluateVO>(true));
		// 添加信息传送会计平台的规则。
		approvePassAfterRules.add(new PredevaluateSendFipAfterRule());
		// 添加更新重新计提日志的规则。
		approvePassAfterRules.add(new UpdateDepLogAfterRuleForPredevaluate<PredevaluateVO>());

		// 添加审批资产评估单时，自动生成并审批通过相应的总部资产的减值单的规则。
		approvePassAfterRules.add(new CreateHeadQuarterAssetBillRule());
		// 添加总部资产的减值单的回写资产卡片历史表信息的规则。
		approvePassAfterRules.add(new HeadquarterWriteBackToCardhistoryAfterRule(true));
		// 添加新增总部资产的减值单的单据日志的规则。
		approvePassAfterRules.add(new HeadquarterCreateBillLogAfterRule(LogTypeConst.predevaluate));
		// 添加更新总部资产的减值单的资产卡片日志的规则。
		approvePassAfterRules.add(new HeadquarterUpdateCardLogAfterRule(true));
		// 添加总部资产的减值单的信息传送会计平台的规则。
		approvePassAfterRules.add(new HeadquarterSendFipAfterRule());
		// 添加更新重新计提日志的规则。
		approvePassAfterRules.add(new HeadquarterUpdateDepLogAfterRule());
		// 添加清空缓存中保存的总部资产的减值单数据的规则。
		approvePassAfterRules.add(new DeleteHeadquarterDataFromContextAfterRule<PredevaluateVO>());

		// 添加审批后的业务规则。
		template.addApprovePassAfterRule(approvePassAfterRules);
	}

	/**
	 * <p>
	 * 取得总部资产对应的财务组织。
	 * </p>
	 *
	 * @param headVO
	 *            资产减值单表头VO
	 * @return String
	 *             财务组织主键
	 * @throws BusinessException
	 *             业务异常
	 */
	private String getHeadAssetGroupPK(PredevaluateHeadVO headVO) throws BusinessException {
		// 取得总部资产表头VO。
		AssetGroupHeadVO assetgroupVO =
				AMProxy.lookup(IAssetGroupService.class)
				.queryOrgAndAssetGroupRate(headVO.getPk_org(), headVO.getPk_assetgroup());

		return assetgroupVO.getPkorg_belong();
	}

	/**
	 * <p>
	 * 取得指定总部资产所属财务组织对应的账簿。
	 * </p>
	 *
	 * @param headVO
	 *            资产减值单表头VO
	 * @return String
	 *             账簿主键
	 * @throws BusinessException
	 *             业务异常
	 */
	private String getHeadAssetGroupAccbook(PredevaluateHeadVO headVO) throws BusinessException {
		// 初始化账簿主键。
		String headOrgAccountingbook = null;

		// 根据资产组的账簿取得账簿分类主键。
		String[] baseAccbook = AccbookChangeUtil.findSetofbookKey(
				headVO.getPk_org(),
				new String[] {headVO.getPk_accbook()},
				headVO.getBusiness_date());
		if (ArrayUtils.isNotEmpty(baseAccbook)) {
			// 取得总部资产对应的财务组织。
			String headAssetGroupPK = getHeadAssetGroupPK(headVO);

			// 根据账簿分类主键取得总部资产所属财务组织的财务核算账簿。
			String[] headOrgAccbook = AccbookChangeUtil.findAccountingBookKey(
					headAssetGroupPK,
					baseAccbook,
					headVO.getBusiness_date());

			if (ArrayUtils.isNotEmpty(headOrgAccbook)) {
				headOrgAccountingbook = headOrgAccbook[0];
			}
		}

		return headOrgAccountingbook;
	}

	/**
	 * <p>
	 * 初始化取消审批资产减值单的规则（含校验）。
	 * </p>
	 *
	 * @see nc.impl.am.bill.BillBaseDAO
	 *              #initUnApproveRule(nc.bs.am.framework.action.approve.UnApproveActionTemplate)
	 */
	@Override
	protected void initUnApproveRule(UnApproveActionTemplate<PredevaluateVO> template) {

		// 初始化取消审批的校验列表。
		List<Validator> validators = new ArrayList<Validator>();
		//添加是否启用账簿的校验
		validators.add(new FAAccbookCheckSealRule());
		// 添加资产卡片是否合法的校验。
		validators.add(new CheckCardLegalValidator());
		//最小未结账月
		validators.add(new CheckMinUnClosedBookPeriodValidator());
		// 添加判断财务是否已经制成折旧清单的校验。
		validators.add(new CheckHasFipMakeVoucherForAccbook());
		// 初始化取消审批前的业务规则。
		List<IRule<PredevaluateVO>> unApprovBeforeRules = new ArrayList<IRule<PredevaluateVO>>();
		// 添加校验。
		unApprovBeforeRules.add(new ValidateServiceRule<PredevaluateVO>(validators));
		// 添加取消审核前的业务规则。
		template.addUnApprovedBeforeRules(unApprovBeforeRules);

		// 初始化取消审批后的业务规则。
		List<IRule<PredevaluateVO>> lastUnApprovedAfterRules = new ArrayList<IRule<PredevaluateVO>>();
		// 添加回写资产卡片历史表信息的规则。
		lastUnApprovedAfterRules.add(new PredevaluateWriteBackToCardhistoryAfterRule(false));
		// 添加删除单据日志的规则。
		lastUnApprovedAfterRules.add(new DeleteBillLogAfterRule<PredevaluateVO>());
		// 添加更新资产卡片日志的规则。
		lastUnApprovedAfterRules.add(new UpdateCardLogAfterRule<PredevaluateVO>(false));
		// 添加信息传送会计平台的规则。
		lastUnApprovedAfterRules.add(new DeleteFipAfterRule<PredevaluateVO>());

		// 添加取消审批资产评估单时，自动取消审批相应的总部资产的减值单的规则。
		lastUnApprovedAfterRules.add(new UnApproveHeadQuarterAssetBillRule());
		// 添加总部资产的减值单的回写资产卡片历史表信息的规则。
		lastUnApprovedAfterRules.add(new HeadquarterWriteBackToCardhistoryAfterRule(false));
		// 添加新增总部资产的减值单的单据日志的规则。
		lastUnApprovedAfterRules.add(new HeadquarterDeleteBillLogAfterRule());
		// 添加更新总部资产的减值单的资产卡片日志的规则。
		lastUnApprovedAfterRules.add(new HeadquarterUpdateCardLogAfterRule(false, true));
		// 添加总部资产的减值单的信息传送会计平台的规则。
		lastUnApprovedAfterRules.add(new HeadquarterDeleteFipAfterRule());
		// 添加清空缓存中保存的总部资产的减值单数据的规则。
		lastUnApprovedAfterRules.add(new DeleteHeadquarterDataFromContextAfterRule<PredevaluateVO>());
		// 添加更新重新计提日志的规则。
		lastUnApprovedAfterRules.add(new UpdateDepLogAfterRuleForPredevaluate<PredevaluateVO>());
		// 添加取消审批后的业务规则。
		template.addLastUnApprovedAfterRules(lastUnApprovedAfterRules);
	}

	/**
	 * <p>
	 * 新增、删除、审批、取消审批减值单时，更新减值单卡片对应的fa_log信息，<br>
	 * 包括：账簿、固定资产卡片、单据号、单据类型、单据状态。
	 * </p>
	 *
	 * @param vo
	 *            固定资产减值单聚合VO
	 * @param cardsByHeadquarters
	 *            总部资产的卡片
	 * @param isClear
	 *            是否清空fa_log中的单据号、单据类型、单据状态
	 *            <ul>
	 *                <li> TRUE：清空；
	 *                <li>FALSE：不清空。
	 *            </ul>
	 * @throws BusinessException
	 *             业务异常
	 */
	private void updateLog(PredevaluateVO vo, String[] cardsByHeadquarters, boolean isClear)
			throws BusinessException {
		// 取得表头数据。
		PredevaluateHeadVO headVO = (PredevaluateHeadVO) vo.getParentVO();
		// 取得资产账簿。
		String pk_accbook = headVO.getPk_accbook();

		// 初期化单据号、单据类型、单据状态。
		String bill_code = null;
		String bill_type = null;
		Integer bill_status = null;

		// 不清空时，取得表头含有的单据号、单据类型、单据状态信息。
		if (!isClear) {
			bill_code = headVO.getBill_code();
			bill_type = headVO.getBill_type();
			bill_status = headVO.getBill_status();
		}

		// 取得表体数据。
		PredevaluateBodyVO[] bodyVOs = (PredevaluateBodyVO[]) vo.getChildrenVO();

		String[] pk_cards = null;
		// cardsByHeadquarters为空，说明不是总部资产，需更新表体的卡片。
		if (ArrayUtils.isEmpty(cardsByHeadquarters)) {
			pk_cards = BaseVOUtils.getVOAttributeValues(bodyVOs,
					PredevaluateBodyVO.PK_CARD);
		} // cardsByHeadquarters非空，说明是总部资产下的卡片，更新之。
		else {
			pk_cards = cardsByHeadquarters;
		}

		// 更新fa_log信息：账簿、固定资产卡片、单据号、单据类型、单据状态。
		getLogService().updateLog(pk_accbook, pk_cards, bill_code, bill_type,
				bill_status);
	}

	/**
	 * <p>
	 * 判断资产组新增时要校验对应的总部资产和同一资产组的卡片<br>
	 * 有没有进行过计提减值（资产组计提减值准备一个月只能计提一次）。
	 * </p>
	 *
	 * @param pk_org
	 *            财务组织
	 * @param pk_accbook
	 *            账簿
	 * @param businessDate
	 *            业务日期
	 * @param assetgroupCards
	 *            当前资产组下的卡片
	 * @return boolean
	 * <ul>
	 *     <li> TRUE: 已计提
	 *     <li>FALSE: 未计提
	 * </ul>
	 * @throws BusinessException
	 *             业务异常
	 */
	@Override
	public boolean hasDonePredevaluate(String pk_org, String pk_accbook,
			UFDate businessDate, String[] assetgroupCards)
					throws BusinessException {

		String sqlform = " from fa_predevaluate a join  fa_predevaluate_b b on a.pk_predevaluate = b.pk_predevaluate "
				+ " where b.pk_card in "
				+ InSqlManager.getInSQLValue(assetgroupCards)
				+ " and b.pk_org = '" + pk_org + "' and pk_accbook = '" + pk_accbook + "' and b.dr = 0 and "
				+ PredevaluateHeadVO.BILL_STATUS
				+ " = "
				+ BillStatusConst.check_pass;
		// 查出已经做过减值的卡片， 但这里没有日期
		PredevaluateHeadVO[] headVOs = QueryUtil.querySuperVOBySQL(
				new String[] { PredevaluateHeadVO.BUSINESS_DATE }, sqlform,
				PredevaluateHeadVO.class);

		for (PredevaluateHeadVO headVO : headVOs) {
			UFDate date = headVO.getBusiness_date();
			if (businessDate.getYear() == date.getYear()
					&& businessDate.getMonth() == date.getMonth()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * <p>
	 * 解锁卡片。
	 * </p>
	 *
	 * @param billVO
	 *            固定资产减值单聚合VO
	 * @param context
	 *            登录信息
	 */
	private void releasePKCardLock(PredevaluateVO billVO) {

		PredevaluateBodyVO[] bodyVOs = (PredevaluateBodyVO[]) billVO.getChildrenVO();

		if (ArrayUtils.isNotEmpty(bodyVOs)) {
			String[] pk_cards = new String[bodyVOs.length];
			for (int i = 0; i < bodyVOs.length; i++) {
				pk_cards[i] = bodyVOs[i].getPk_card();
			}
			LockManager.releasePKLocks(pk_cards, InvocationInfoProxy.getInstance().getUserId());
		}
	}

	/**
	 * <p>
	 * 取得固定资产卡片服务。
	 * </p>
	 *
	 * @return IAssetService 固定资产卡片服务
	 */
	private IAssetService getAssetCardService() {
		return AMProxy.lookup(IAssetService.class);
	}

	/**
	 * <p>
	 * 取得固定资产LOG服务。
	 * </p>
	 *
	 * @return ILogService 固定资产LOG服务
	 */
	private ILogService getLogService() {
		return AMProxy.lookup(ILogService.class);
	}

	/**
	 * <p>
	 * 联查固定资产减值单。
	 * </p>
	 *
	 * @param pk
	 *            卡片主键
	 * @return PredevaluateVO[]
	 *             固定资产减值单聚合VO数组
	 * @throws BusinessException
	 *             业务异常
	 */
	@Override
	public PredevaluateVO[] queryAbout(String pk) throws BusinessException {
		return QueryUtil.queryBillVOByPks(PredevaluateVO.class, new String[]{pk}, true);
	}

	@Override
	public PredevaluateVO commit(PredevaluateVO[] billVOs)
			throws BusinessException {
		return ArrayUtils.getFirstElem(commitBillVO(billVOs));
	}



}///:~
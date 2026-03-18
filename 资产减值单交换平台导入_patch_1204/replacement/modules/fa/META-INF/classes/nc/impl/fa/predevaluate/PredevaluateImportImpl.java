package nc.impl.fa.predevaluate;

import java.util.ArrayList;
import java.util.List;

import nc.bs.am.framework.action.IActionTemplate;
import nc.bs.am.framework.common.rule.ValidateServiceRule;
import nc.bs.uif2.validation.Validator;
import nc.impl.am.bill.rule.BillCodeInsertAfterRule;
import nc.impl.am.bill.rule.BillCodeInsertBeforeRule;
import nc.impl.am.db.QueryUtil;
import nc.impl.fa.common.rule.UpdateCardLogAfterRule;
import nc.impl.fa.common.validator.CheckCardLegalValidator;
import nc.impl.fa.common.validator.CheckHasFipMakeVoucherForAccbook;
import nc.impl.fa.common.validator.CheckMinUnClosedBookPeriodValidator;
import nc.impl.fa.predevaluate.rule.CheckSameAccbookValidator;
import nc.impl.fa.rule.FAAccbookCheckSealRule;
import nc.itf.fa.prv.IPredevaluateImport;
import nc.pub.fa.common.manager.VOManager;
import nc.pub.fa.common.util.StringUtils;
import nc.vo.am.common.util.ArrayUtils;
import nc.vo.fa.asset.AssetVO;
import nc.vo.fa.predevaluate.PredevaluateBodyVO;
import nc.vo.fa.predevaluate.PredevaluateHeadVO;
import nc.vo.fa.predevaluate.PredevaluateVO;
import nc.vo.pub.BusinessException;
import nc.vo.uif2.LoginContext;

public class PredevaluateImportImpl extends PredevaluateImpl implements IPredevaluateImport {

	@Override
	public PredevaluateVO queryPredevaluateVOByPk(String... pks) {
		// 查询单据数据。
		PredevaluateVO[] aggVO = QueryUtil.queryBillVOByPks(PredevaluateVO.class, pks, true);
		if (ArrayUtils.isNotEmpty(aggVO)) {
			return aggVO[0];
		} else {
			return null;
		}
	}
	
	@Override
	public PredevaluateVO insertPredevaluateVO(LoginContext context, PredevaluateVO vo) throws BusinessException{
		return super.insertPredevaluateVO(context, vo);
	}
	
	/**
	 * 设置保存前后规则
	 */
	@Override
	protected void initInsertActionRules(IActionTemplate<PredevaluateVO> insertAction) {

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

}

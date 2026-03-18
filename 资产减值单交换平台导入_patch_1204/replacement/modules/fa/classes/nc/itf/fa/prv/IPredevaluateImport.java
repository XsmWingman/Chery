package nc.itf.fa.prv;

import nc.vo.fa.predevaluate.PredevaluateVO;
import nc.vo.pub.BusinessException;
import nc.vo.uif2.LoginContext;

/**
 * 资产减值单外部导入
 * @author Lenovo
 *
 */
public interface IPredevaluateImport {
	/**
	 * 根据PK查询资产减值单
	 */
	public PredevaluateVO queryPredevaluateVOByPk(String... pks);
	
	public PredevaluateVO insertPredevaluateVO(LoginContext context, PredevaluateVO vo) throws BusinessException;
	
}

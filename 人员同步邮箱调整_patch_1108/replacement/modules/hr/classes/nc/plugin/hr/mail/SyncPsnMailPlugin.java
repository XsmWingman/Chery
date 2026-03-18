package nc.plugin.hr.mail;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import nc.bs.bd.pub.SyncLogTools;
import nc.bs.dao.BaseDAO;
import nc.bs.dao.DAOException;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.logging.Logger;
import nc.bs.mail.http.mail.MailHttpClient;
import nc.bs.pub.pa.PreAlertObject;
import nc.bs.pub.taskcenter.BgWorkingContext;
import nc.bs.pub.taskcenter.IBackgroundWorkPlugin;
import nc.itf.mail.service.IMailSyncDataRequiresNew;
import nc.jdbc.framework.processor.ColumnListProcessor;
import nc.jdbc.framework.processor.ColumnProcessor;
import nc.jdbc.framework.processor.MapProcessor;
import nc.vo.ecpubapp.tools.DBTool;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.pub.lang.UFDateTime;
import nc.vo.uapbd.synclog.DevSyncLogVO;

public class SyncPsnMailPlugin implements IBackgroundWorkPlugin {

	@Override
	public PreAlertObject executeTask(BgWorkingContext bgwc)
			throws BusinessException {
		send();
		return null;
	}
	
//	发送请求
	private void send(){
		String requestInfo = "", responseInfo = "";
		try {
			Map<String,Object> sysInfo = getSysInfo();
//			JSONObject tokenBody = new JSONObject();
			if(sysInfo==null || sysInfo.size()==0 || null == sysInfo.get("dataurl") || null == sysInfo.get("headercode")){
				throw new Exception("外系统配置未维护，请维护外系统配置信息【v_ehr_sync_emailinfo】!");
			}
			String authorization = String.valueOf(sysInfo.get("headercode"));
			String dataURL = String.valueOf(sysInfo.get("dataurl"));
//			String token = QrjrHttpClient.doPostLogin(tokenURL,sysInfo.get("loginid").toString(), sysInfo.get("secretkey").toString());
			
//			拼装请求参数
			JSONObject dataBody = new JSONObject();
//			dataBody.put("token", token);
			
//			检查是否更新过
			if(checkInit()){
				String updateTime = findUpdateDate();
				List<String> psncodes = new ArrayList<>();
				dataBody.put("employeeNoList", psncodes);
				dataBody.put("updateTimeStart", updateTime);
				requestInfo = dataBody.toString();	 
				// 同步数据
				JSONObject dataResult = JSONObject.parseObject(MailHttpClient.sendPostRequest(dataURL, authorization, dataBody.toString()));
				
				responseInfo = dataResult.toString();
				if (null != dataResult.getString("rtnCode") && (dataResult.getString("rtnCode").equals("S") || dataResult.getString("rtnCode").equals("W"))) {
					updatePsnEmail(dataResult.getJSONObject("rtnData").getJSONArray("data"));
				}else{
					throw new Exception("响应报文错误");
				}
				
			}else{
//				1000条一批，循环进行查询
//				查询人员工号
				for(int i = 1; true; i+=1000){
					int end = i+1000;
					String psnsql = "SELECT code FROM "
							+ "(SELECT A.code, ROWNUM AS rn FROM (SELECT code FROM bd_psndoc where enablestate=2 ORDER BY code) A "
							+ "WHERE ROWNUM < " + end  + ") WHERE rn >= " + i;
					List<String> psncodes = (List<String>) new BaseDAO().executeQuery(psnsql, new ColumnListProcessor());
					if(null == psncodes || psncodes.size() == 0){
						break;
					}
					dataBody.put("employeeNoList", psncodes);
					requestInfo = dataBody.toString();
					// 同步数据
					JSONObject dataResult = JSONObject.parseObject(MailHttpClient.sendPostRequest(dataURL, authorization, dataBody.toString()));
					responseInfo = dataResult.toString();
					if (null != dataResult.getString("rtnCode") && (dataResult.getString("rtnCode").equals("S") || dataResult.getString("rtnCode").equals("W"))) {
						updatePsnEmail(dataResult.getJSONObject("rtnData").getJSONArray("data"));
					}else{
						throw new Exception("响应报文错误");
					}
					if(psncodes.size() < 1000){
						break;
					}
				}
			}
		} catch (Exception e) {
			writeLog(requestInfo, responseInfo, false, e.getMessage());
			return;
		}
		writeLog(requestInfo, responseInfo, true, "");

	}
	
 
	
//	获取登录信息
	private Map<String,Object> getSysInfo() throws DAOException{
		String sql = "select * from v_ehr_sync_emailinfo" ;
		return (Map<String,Object>)new BaseDAO().executeQuery(sql, new MapProcessor());
	}
	
//	检查是否存在更新成功的记录日志
	private Boolean checkInit() throws DAOException{
		String sql = "select count(*) from dev_synclog where vdef2='nc.plugin.hr.mail.SyncPsnMailPlugin' and syncsucc = 'Y'";
		Object num = new BaseDAO().executeQuery(sql, new ColumnProcessor());
		if(null != num && Integer.parseInt(String.valueOf(num)) > 0){
			return true;
		}
		return false;
	}
	
//	查找最新的更新日期：yyyy-MM-dd hh:mm:ss
	private String findUpdateDate() throws DAOException{
		String sql = "select ts from (select ts from dev_synclog where syncsucc = 'Y' and vdef2='nc.plugin.hr.mail.SyncPsnMailPlugin' order by ts desc) where rownum = 1";
		String date = (String) new BaseDAO().executeQuery(sql, new ColumnProcessor());
		return date;
	}
	
//	更新人员邮箱信息
	private void updatePsnEmail(JSONArray psnArray) throws Exception {
		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			String sql = "select cuserid from sm_user where user_code='IAMCESHI'";
			String cuserid = (String) new BaseDAO().executeQuery(sql, new ColumnProcessor());
			UFDateTime now = new UFDateTime();
			conn = new DBTool().getConnection();
			pstmt = conn.prepareStatement("update bd_psndoc set glbdef13 = ?,modifiedtime = ?,modifier = ? where code = ?");
			conn.setAutoCommit(false);
			int count = 0;
			for(int i=0; i<psnArray.size(); i++){
				JSONObject data = psnArray.getJSONObject(i);
				String email = data.getString("enterpriseEmail");
				String code = data.getString("employeeNo");
				if(email != null && code != null){
					count++;
					pstmt.setString(1, email);
					pstmt.setString(2, now.toLocalString());
					pstmt.setString(3, cuserid);
					pstmt.setString(4, code);
					pstmt.addBatch();
				}
				if(count == 1000 || i == psnArray.size()-1){
					count = 0;
					pstmt.executeBatch();
					pstmt.clearBatch();
					conn.commit();
				}
			}
		} catch (SQLException e) {
			if(null != conn)
				conn.rollback();
			throw new Exception("批量更新错误：" + e.getMessage());
		} finally{
			if(null != pstmt)
				pstmt.close();
			if(null != conn)
				conn.close();
		}
	}
	
	private void writeLog(String requestInfo, String responseInfo, boolean succ,String errmsg) {
		DevSyncLogVO syncVO = null;
		try {
			if(requestInfo.getBytes(StandardCharsets.UTF_8).length >= 4000){
				requestInfo = requestInfo.substring(0, 1300);
			}
			if(responseInfo.getBytes(StandardCharsets.UTF_8).length >= 4000){
				responseInfo = responseInfo.substring(0, 1300);
			}
			syncVO = null;//getDevSyncLogVO(datacode,pk_org);
			
			if(syncVO==null){
				syncVO = new DevSyncLogVO();
			}
			syncVO.setTargetsystem("IAM");
			syncVO.setDirection("IAM->EHR");
			syncVO.setPk_org("");
			syncVO.setPk_data("");
			syncVO.setDatacode("");
			syncVO.setDataname("人员邮箱同步");
			syncVO.setDatatype("IAM");
			syncVO.setSyncuser(InvocationInfoProxy.getInstance().getUserId());
			UFDateTime synctime = new UFDateTime();
			syncVO.setSyncdate(synctime.getDate());
			syncVO.setSynctime(synctime);
			syncVO.setPk_group(InvocationInfoProxy.getInstance().getGroupId());
			syncVO.setEventtype("");
			syncVO.setSourceid("");
			syncVO.setVdef2("nc.plugin.hr.mail.SyncPsnMailPlugin");
			syncVO.setOpertype("18");
			syncVO = SyncLogTools.setRequestInfo(syncVO,requestInfo);
			syncVO.setResponsemsg(responseInfo);
			if(succ){
				syncVO.setSyncsucc(UFBoolean.TRUE);
				syncVO.setSyncinfo("同步成功");
			}
			else{
				syncVO.setSyncsucc(UFBoolean.FALSE);
				syncVO.setSyncinfo(errmsg);
			}
			UFDateTime datetime = new UFDateTime();
			syncVO.setSyncdate(datetime.getDate());
			syncVO.setSynctime(datetime);
			syncVO.setEventobj(null);
			syncVO.setModifierinfo("");
			if (syncVO.getCreator() == null) {
				syncVO.setCreator(InvocationInfoProxy.getInstance().getUserId());
				syncVO.setCreationtime(new UFDateTime());
			}
			NCLocator.getInstance().lookup(IMailSyncDataRequiresNew.class).insertAndUpdate__RequiresNew(new SuperVO[] { syncVO },"");
		} catch (Exception e) {
			syncVO.setSyncsucc(new UFBoolean(false));
			syncVO.setSyncinfo(e.getLocalizedMessage());
			UFDateTime datetime = new UFDateTime();
			syncVO.setSyncdate(datetime.getDate());
			syncVO.setSynctime(datetime);
			syncVO.setEventobj(null);
			NCLocator.getInstance().lookup(IMailSyncDataRequiresNew.class).insertAndUpdate__RequiresNew(new SuperVO[] { syncVO },"");
			Logger.error(e.getMessage(), e);
		}
	}

}

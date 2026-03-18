package nc.bs.mail.http.mail;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;

import com.alibaba.fastjson.JSONObject;

public class MailHttpClient {
	
	 public static void main(String[] args) {
	        // 1. 目标API端点
//	        String apiUrl = "https://ipaas-uat.mychery.com/api_bd/chery_esb/AMS/SRC/A1601/CD";
	    	
	    	String apiUrl = "https://ipaas.mychery.com/api_bd/chery_esb/IAM/RCV/A001/FA";
	        
	        
	        // 2. 准备JSON格式的请求参数
//	        String jsonInputString = "{\"qj\": \"NC\"}";
//	        String token = "Basic TkM6Y2hlcnlOQzIwMjE=";
	        
	        String jsonInputString = "{\"employeeNo\": \"\",\"updateTimeStart\": \"2026-01-31 15:00:00\",\"updateTimeEnd\": \"2026-02-01 15:00:00\",\"employeeNoList\":[]}";
	        String token = "Basic TkM6Y2hlcnlOQzIwMjE=";
	        
	        try {
				String returnMsg = sendPostRequest(apiUrl, token,jsonInputString);
				System.out.println("returnMsg>>"+returnMsg);
			} catch (Exception e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
			}
	 }

	public static String doPost(String url,String body, String authorization) throws Exception {
		// 在调用请求前设置系统属性
		Request request = Request.Post(url);
		request.addHeader("Authorization", authorization);
        request.bodyString(body, ContentType.APPLICATION_JSON);
        request.addHeader("Content-Type", "application/json");
        HttpResponse httpResponse = request.execute().returnResponse();
        if (httpResponse.getEntity() != null) {
        	return EntityUtils.toString(httpResponse.getEntity(),"UTF-8");
        }
		JSONObject msg = new JSONObject();
		msg.put("success", "");
		return msg.toJSONString();
	}
	
	public static String doPostLogin(String url, String loginId, String secretKey) throws Exception {
		Request request = Request.Post(url);
        request.addHeader("loginId", loginId);
        request.addHeader("secretKey", secretKey);
        HttpResponse httpResponse = request.execute().returnResponse();
        if (httpResponse.getEntity() != null) {
        	return EntityUtils.toString(httpResponse.getEntity(),"UTF-8");
        }
		JSONObject msg = new JSONObject();
		msg.put("success", "");
		return msg.toJSONString();
	}
	
	/**
     * 发送JSON格式的POST请求
     * 
     * @param urlStr 目标URL
     * @param jsonData JSON格式的请求体
     * @return 响应内容
     */
    public static String sendPostRequest(String urlPath, String token, String jsonData) throws Exception {
    	HttpsURLConnection httpsConn = null;
		HttpURLConnection httpConn = null;
		DataOutputStream out = null;
		BufferedReader reader = null;
		String strResponse = ""; 
		StringBuilder responseBuilder = new StringBuilder();
		
		try {
			URL myURL = new URL(urlPath);
			if (urlPath.startsWith("https://")) {
				System.setProperty("https.protocols", "TLSv1.2");
				httpsConn = (HttpsURLConnection) myURL.openConnection();
				TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}

					public void checkClientTrusted(
							java.security.cert.X509Certificate[] certs,
							String authType) {
					}

					public void checkServerTrusted(
							java.security.cert.X509Certificate[] certs,
							String authType) {
					}
				} };
				SSLContext sc = SSLContext.getInstance("TLSv1.2");
				sc.init(null, trustAllCerts, new java.security.SecureRandom());
				httpsConn.setSSLSocketFactory(sc.getSocketFactory());
				HostnameVerifier hv = new HostnameVerifier() {
					@Override
					public boolean verify(String urlHostName, SSLSession session) {
						return true;
					}
				};
				httpsConn.setHostnameVerifier(hv);

				httpsConn.setDoOutput(true);
				httpsConn.setDoInput(true); //这个是必须的
				httpsConn.setUseCaches(false);//忽略缓存
				// 设置请求方法类型为 默认为GET 此处必须为大写
				httpsConn.setRequestMethod("POST");
				// 设置连接超时
				httpsConn.setConnectTimeout(10000);
	            // 设置读取超时
				httpsConn.setReadTimeout(20000);
				httpsConn.setRequestProperty("Connection", "Keep-Alive");
				httpsConn.setRequestProperty("Accept", "*/*");
//				httpsConn.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
				httpsConn.setRequestProperty("Charset", "UTF-8");
				httpsConn.setRequestProperty("Content-Type", "application/json; utf-8");
				httpsConn.setRequestProperty("User-Agent","java HttpsURLConnection");	
				httpsConn.setRequestProperty("Authorization", token);
				httpsConn.setInstanceFollowRedirects(true);
				
			 
				// 开始连接 connection.getOutputStream()已包含此操作
//				httpsConn.connect();
				// 如果响应内容乱码在此处添加编码名称 
	            out = new DataOutputStream(httpsConn.getOutputStream());
				
				// 发送参数
	            out.write(jsonData.getBytes("UTF-8")); 
				out.flush();
				out.close();
				// 获取服务端的反馈
				String strLine = "";			
				
				// 取得该连接的输入流，以读取响应内容
				reader = new BufferedReader(new InputStreamReader(
						httpsConn.getInputStream(), "UTF-8"));
				int code = httpsConn.getResponseCode();				
				while((strLine = reader.readLine()) != null) {
					responseBuilder.append(strLine);
				}
				
			} else {
				// 服务地址
				URL url = new URL(urlPath);
				// 设定连接的相关参数
				httpConn = (HttpURLConnection) url.openConnection();	
				// 设置是否向HttpURLhttpConn输出 post请求参数要放在http正文内需设为true 默认false
				httpConn.setDoOutput(true);
				httpConn.setDoInput(true); //这个是必须的
				httpConn.setUseCaches(false);//忽略缓存
				// 设置请求方法类型为 默认为GET 此处必须为大写
				httpConn.setRequestMethod("POST");
				// 设置连接超时
	            httpConn.setConnectTimeout(10000);
	            // 设置读取超时
	            httpConn.setReadTimeout(20000);
	            httpConn.setRequestProperty("httpConn", "Keep-Alive");
	            httpConn.setRequestProperty("Accept", "*/*");
	            httpConn.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
	            httpConn.setRequestProperty("Charset", "utf-8");
	            httpConn.setRequestProperty("Content-Type", "application/json; utf-8");
	            httpConn.setRequestProperty("user-agent",
						"java HttpURLConnection");
	            // 开始连接 httpConn.getOutputStream()已包含此操作
	             httpConn.connect();
				// 如果响应内容乱码在此处添加编码名称 
	            out = new DataOutputStream(httpConn.getOutputStream());
				
				// 发送参数
	            out.write(jsonData.getBytes()); 
				out.flush();
				out.close();
				// 获取服务端的反馈
				String strLine = "";
				strResponse = "";
				int code = httpConn.getResponseCode();
				
				InputStream in = httpConn.getInputStream();
				reader = new BufferedReader(new InputStreamReader(in,"UTF-8"));
				while((strLine = reader.readLine()) != null) {
					responseBuilder.append(strLine);
				}
			}
			 
			return responseBuilder.toString();
		} catch (IOException e) {
			e.printStackTrace();
			return null; 
		} catch (Exception e) {
			 
			return null;
		} finally {
			 
			if (out != null) {
				try {
					if(out != null) {
						out.close();
					}
					if(reader != null){
						reader.close();
					}					 
				} catch (IOException e) {
					e.printStackTrace();				 
					return null;
				}
			}
			if (httpConn != null) {
				httpConn.disconnect();
			}
			if (httpsConn != null) {
				httpsConn.disconnect();
			}
			 		
		}            
            
    }
}

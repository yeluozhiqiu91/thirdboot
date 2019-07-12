package com.wang.utils;

import com.google.gson.Gson;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.AbstractContentBody;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.Map.Entry;

/**
 * @Author: wangliujie
 * @Date: 2019/07/12 16:15
 */
public class HttpClientUtil {

    private static final Logger LOG = LoggerFactory.getLogger(HttpClientUtil.class);

    private static final String DEFAULT_CHARSET = "GBK";

    /**
     * 连接超时时间，由bean factory设置，缺省为8秒钟
     **/
    private static int defaultConnectionTimeout = 60000;

    /**
     * 回应超时时间, 由bean factory设置，缺省为30秒钟
     */
    private static int defaultSoTimeout = 60000;

    /**
     * 闲置连接超时时间, 由bean factory设置，缺省为60秒钟
     */
    private static HttpConnectionManager connectionManager = ConnectionManagerPool.getInstance().connectionManager;

    private static HttpClientUtil httpProtocolHandler = new HttpClientUtil();

    private static SSLConnectionSocketFactory sslSocketFactory;

    private  static PoolingHttpClientConnectionManager poolingConnectionManager ;


    static {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new HttpsX509TrustManager()}, new java.security.SecureRandom());
            sslSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

            poolingConnectionManager = new PoolingHttpClientConnectionManager(
                    RegistryBuilder.<ConnectionSocketFactory>create().register("http",
                            PlainConnectionSocketFactory.INSTANCE).register("https", sslSocketFactory).build());
        } catch (Exception e) {
            LOG.error("init poolingConnectionManager failed.", e);
        }
    }

    private HttpClientUtil() {
    }
    public static SSLConnectionSocketFactory getsslSocketFactory (){
        return sslSocketFactory;
    }

    /**
     * 工厂方法
     *
     * @return
     */
    public static HttpClientUtil getInstance() {
        return httpProtocolHandler;
    }

    /**
     * @param url
     * @return
     */
    public static String doGet(String url) {
        return doGet(url, null);
    }

    private static void closeClient(CloseableHttpClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                LOG.error("When CloseableHttpClient close, get IOException");
            }
        }
    }

    /**
     * 支持https
     * @param url
     * @param headers
     * @return
     */
    public static String doGet(String url, Map<String, String> headers) {

        long startTime = System.currentTimeMillis();

        CloseableHttpClient client = null;
        HttpGet httpGet = null;
        CloseableHttpResponse response = null;

        try {

            String charset = getCharset(headers, "utf-8");

            client = createHttpClient();
            httpGet = createHttpMethod(new HttpGet(url), headers);
            httpGet.setConfig(getRequestConfigDefault());

            return execute(client, httpGet, headers, url, charset);

        } catch (Exception e) {
            LOG.warn(url + " request error: " + e.getMessage(), e);
        } finally {
            LOG.info("execute " + url + " used time: " + (System.currentTimeMillis() - startTime) + "ms");
            if (httpGet != null) {
                httpGet.releaseConnection();
            }
            closeClient(client);
        }

        return null;

    }

    /**
     * 支持htpps
     * @param url
     * @return
     */
    public static String doGet(String url, Map<String, String> headers,List<CloseableHttpResponse> resultList) {

        long startTime = System.currentTimeMillis();
        String charset = "utf-8";
        CloseableHttpClient client = null;
        HttpGet httpGet = null;

        try {
            //创建客户端
            client = createHttpClient();
            //创建头与方法
            httpGet = createHttpMethod(new HttpGet(url), headers);
            httpGet.setConfig(getRequestConfigDefault());
            //发送请求
            CloseableHttpResponse response = client.execute(httpGet);

            //保存响应
            if(resultList != null){
                resultList.add(response);
            }

            int statusCode = response.getStatusLine().getStatusCode();
            String responseText = EntityUtils.toString(response.getEntity(), charset);

            if (statusCode == HttpStatus.SC_OK ) {
                return responseText;
            } else {
                LOG.warn(url + " request failed: " + responseText);
                if (StringUtils.isNotEmpty(responseText) && responseText.length() > 256) {
                    responseText = responseText.substring(0, 256);
                }
                return "httpStatus=" + statusCode + "&httpMessage=" + StringUtils.defaultString(responseText);
            }

        } catch (Exception e) {
            LOG.error(url + " request error: " + e.getMessage(), e);
        } finally {
            LOG.info("execute " + url + " used time: " + (System.currentTimeMillis() - startTime) + "ms");
            if (httpGet != null) {
                httpGet.releaseConnection();
            }
            closeClient(client);
        }
        return "";

    }

    /**
     * 支持https
     * get头与cookie处理
     * @param url
     * @param headers
     * @param resultList
     * @param cookiesReceiver
     * @return
     */
    public static String doGet(String url, Map<String, String> headers,Map<String,String> cookiesReceiver,List<CloseableHttpResponse> resultList){

        long startTime = System.currentTimeMillis();
        String charset = "utf-8";
        CloseableHttpClient client = null;
        HttpGet httpGet = null;

        try {
            //创建客户端
            client = createHttpClient();
            //创建头与方法
            httpGet = createHttpMethod(new HttpGet(url), headers);
            httpGet.setConfig(getRequestConfigDefault());
            //发送请求
            CloseableHttpResponse response = client.execute(httpGet);

            //保存响应
            if(resultList != null){
                resultList.add(response);
            }

            setCookie(client,cookiesReceiver);

            int statusCode = response.getStatusLine().getStatusCode();
            String responseText = EntityUtils.toString(response.getEntity(), charset);

            if (statusCode == HttpStatus.SC_OK ) {
                return responseText;
            } else {
                LOG.warn(url + " request failed: " + responseText);
                if (StringUtils.isNotEmpty(responseText) && responseText.length() > 256) {
                    responseText = responseText.substring(0, 256);
                }
                return "httpStatus=" + statusCode + "&httpMessage=" + StringUtils.defaultString(responseText);
            }

        } catch (Exception e) {
            LOG.error(url + " request error: " + e.getMessage(), e);
        } finally {
            LOG.info("execute " + url + " used time: " + (System.currentTimeMillis() - startTime) + "ms");
            if (httpGet != null) {
                httpGet.releaseConnection();
            }
            closeClient(client);
        }
        return "";
    }

    /**
     * 支持https
     * get头与cookie处理
     * @param url
     * @param headers
     * @param resultList
     * @param cookiesReceiver
     * @return
     */
    public static String doGetNotRedirects(String url, Map<String, String> headers,Map<String,String> cookiesReceiver,List<CloseableHttpResponse> resultList){

        long startTime = System.currentTimeMillis();
        String charset = "utf-8";
        CloseableHttpClient client = null;
        HttpGet httpGet = null;

        try {
            //创建客户端
            client = createHttpClient();
            //创建头与方法
            httpGet = createHttpMethod(new HttpGet(url), headers);
            httpGet.setConfig(getRequestConfigRedict(false));
            //发送请求
            CloseableHttpResponse response = client.execute(httpGet);

            //保存响应
            if(resultList != null){
                resultList.add(response);
            }

            setCookie(client,cookiesReceiver);

            int statusCode = response.getStatusLine().getStatusCode();
            String responseText = EntityUtils.toString(response.getEntity(), charset);

            if (statusCode == HttpStatus.SC_OK ) {
                return responseText;
            } else {
                LOG.warn(url + " request failed: " + responseText);
                if (StringUtils.isNotEmpty(responseText) && responseText.length() > 256) {
                    responseText = responseText.substring(0, 256);
                }
                return "httpStatus=" + statusCode + "&httpMessage=" + StringUtils.defaultString(responseText);
            }

        } catch (Exception e) {
            LOG.error(url + " request error: " + e.getMessage(), e);
        } finally {
            LOG.info("execute " + url + " used time: " + (System.currentTimeMillis() - startTime) + "ms");
            if (httpGet != null) {
                httpGet.releaseConnection();
            }
            closeClient(client);
        }
        return "";
    }

    private static CloseableHttpClient createHttpClient() {
        ConnectionKeepAliveStrategy connectionKeepAliveStrategy = new ConnectionKeepAliveStrategy() {
            @Override
            public long getKeepAliveDuration(HttpResponse httpResponse, HttpContext httpContext) {
                // tomcat默认keepAliveTimeout为20s
                return 19 * 1000;
            }
        };
        return HttpClients.custom().setConnectionManager(poolingConnectionManager)
                .setConnectionManagerShared(true)
//                .setProxy(new org.apache.http.HttpHost("localhost",8888))//test
//                .setProxy(new org.apache.http.HttpHost("10.20.1.110",8081))//test
                .setRetryHandler(new HttpRequestRetryHandler() {
                    @Override
                    public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                        if (executionCount > 3) {
                            LOG.warn("Maximum tries reached for client http pool ");
                            return false;
                        }

                        if (exception instanceof NoHttpResponseException     //NoHttpResponseException 重试
                                || exception instanceof ConnectTimeoutException //连接超时重试
//                                || exception instanceof SocketTimeoutException    //响应超时不重试，避免造成业务数据不一致
                        ) {
                            LOG.warn("NoHttpResponseException on " + executionCount + " call");
                            return true;
                        }
                        return false;
                    }
                })
                .setKeepAliveStrategy(connectionKeepAliveStrategy)
                .build();
    }

    /**
     * @param url
     * @param param
     * @return
     */
    public static String doPost(String url, String param) {
        return doPost(url, param, (Map<String, String>) null);
    }

    /**
     * @param url
     * @param params
     * @param contentType
     * @return
     */
    public static String doPost(String url, String params, String contentType) {
        return doPost(url, params, contentType, null);
    }

    /**
     * @param url
     * @param params
     * @param requestProperties
     * @return
     */
    public static String doPost(String url, String params, Map<String, String> requestProperties) {
        return doPost(url, params, "application/x-www-form-urlencoded", requestProperties);
    }

    /**
     * @param url
     * @param params
     * @param requestProperties
     * @return
     */
    public static String doPostByStream(String url, String params, Map<String, String> requestProperties) {
        return doPost(url, params, "*/*", requestProperties);
    }

    /**
     * 支持https
     * @param url
     * @param params
     * @param headers
     * @return
     */
    public static String doPost(String url, String params, String contentType, Map<String, String> headers) {

        long startTime = System.currentTimeMillis();

        CloseableHttpClient client = null;
        HttpPost httpPost = null;

        try {

            String charset = getCharset(headers, "utf-8");

            client = createHttpClient();


            httpPost = createHttpMethod(new HttpPost(url), headers);
            httpPost.setConfig(getRequestConfigDefault());

            if (StringUtils.isNotEmpty(params)) {
                httpPost.setEntity(new StringEntity(params, ContentType.create(contentType, charset)));
            }

            return execute(client, httpPost, headers, url, charset);

        } catch (Exception e) {
            LOG.error(url + " request error: " + e.getMessage(), e);
        } finally {
            LOG.info("execute " + url + " used time: " + (System.currentTimeMillis() - startTime) + "ms");
            if (httpPost != null) {
                httpPost.releaseConnection();
            }
            closeClient(client);
        }

        return "";
    }

    private static RequestConfig getRequestConfigDefault(){
        return RequestConfig.custom()
                //连接远程的等待时间
                .setConnectTimeout(defaultConnectionTimeout)
                //从连接池获取的等待时间
                .setConnectionRequestTimeout(defaultConnectionTimeout)
                //读取等待时间
                .setSocketTimeout(defaultSoTimeout)
                .build();
    }

    private static RequestConfig getRequestConfigRedict(boolean redirectsEnabled){
        return RequestConfig.custom()
                //连接远程的等待时间
                .setConnectTimeout(defaultConnectionTimeout)
                //从连接池获取的等待时间
                .setConnectionRequestTimeout(defaultConnectionTimeout)
                //读取等待时间
                .setSocketTimeout(defaultSoTimeout)
                .setRedirectsEnabled(redirectsEnabled)
                .build();
    }

    private static String execute(CloseableHttpClient client, HttpRequestBase httpMethod, Map<String, String> headers, String url, String charset)
            throws IOException, ClientProtocolException {

        CloseableHttpResponse response = null;

        boolean returnHttpStatus = getParameter(headers, "Http-Status", false);
        boolean acceptErrorResult = getParameter(headers, "Read-Error", false);

        response = client.execute(httpMethod);

        int statusCode = response.getStatusLine().getStatusCode();
        String responseText = EntityUtils.toString(response.getEntity(), charset);

        if (statusCode == HttpStatus.SC_OK || acceptErrorResult) {
            return responseText;
        } else {
            LOG.warn(url + " request failed: " + responseText);
            if (returnHttpStatus) {
                if (StringUtils.isNotEmpty(responseText) && responseText.length() > 256) {
                    responseText = responseText.substring(0, 256);
                }
                return "httpStatus=" + statusCode + "&httpMessage=" + StringUtils.defaultString(responseText);
            }
        }

        return null;
    }

    /**
     * @param url
     * @param param
     * @return
     */
    public static byte[] downloadPost(String url, String param) {
        return downloadPost(url, param, (Map<String, String>) null);
    }
    /**
     * @param url
     * @param params
     * @param requestProperties
     * @return
     */
    public static byte[] downloadPost(String url, String params, Map<String, String> requestProperties) {
        return downloadPost(url, params, "application/x-www-form-urlencoded", requestProperties);
    }
    /**
     * @param url
     * @param params
     * @param headers
     * @return
     */
    public static byte[] downloadPost(String url, String params, String contentType, Map<String, String> headers) {

        long startTime = System.currentTimeMillis();

        CloseableHttpClient client = null;
        HttpPost httpPost = null;

        try {

            String charset = getCharset(headers, "utf-8");

            client = createHttpClient();

            httpPost = createHttpMethod(new HttpPost(url), headers);
            httpPost.setConfig(getRequestConfigDefault());

            if (StringUtils.isNotEmpty(params)) {
                httpPost.setEntity(new StringEntity(params, ContentType.create(contentType, charset)));
            }

            return executeFile(client, httpPost, headers, url, charset);

        } catch (Exception e) {
            LOG.error(url + " request error: " + e.getMessage(), e);
        } finally {
            LOG.info("execute " + url + " used time: " + (System.currentTimeMillis() - startTime) + "ms");
            if (httpPost != null) {
                httpPost.releaseConnection();
            }
            closeClient(client);
        }

        return null;
    }

    private static byte[]  executeFile(CloseableHttpClient client, HttpRequestBase httpMethod, Map<String, String> headers, String url, String charset)
            throws IOException, ClientProtocolException {

        CloseableHttpResponse response = null;

        boolean acceptErrorResult = getParameter(headers, "Read-Error", false);

        response = client.execute(httpMethod);

        int statusCode = response.getStatusLine().getStatusCode();
        byte[] responseText = EntityUtils.toByteArray(response.getEntity());

        if (statusCode == HttpStatus.SC_OK || acceptErrorResult) {
            return responseText;
        } else {
            return null;
        }

    }

    @SuppressWarnings("unchecked")
    private static <T> T getParameter(Map<String, String> headers, String propertyName, T defaultValue) {

        if (headers == null || headers.isEmpty()) {
            return defaultValue;
        }

        String value = headers.get(propertyName);

        if (StringUtils.isNotBlank(value)) {
            Class<T> clazz = (Class<T>) defaultValue.getClass();
            if (clazz == Integer.class) {
                return (T) Integer.valueOf(value);
            }
            if (clazz == Boolean.class) {
                return (T) Boolean.valueOf("true".equals(value));
            }
            return (T) value;
        }

        return defaultValue;

    }

    private static String getCharset(Map<String, String> headers, String defaultValue) {
        if (headers == null || headers.isEmpty()) {
            return defaultValue;
        }
        String ct = headers.get("Content-Type");
        if (StringUtils.isNotEmpty(ct)) {
            int a = ct.lastIndexOf("charset");
            if (a > -1) {
                return ct.substring(a + 8);
            }
        }
        return defaultValue;
    }

    /**
     * 该方法不支持https
     *
     * @param url
     * @param parameters
     * @return
     */
    public static String doPost(String url, Map<String, String> parameters) {
        HttpClient client = new HttpClient();
        PostMethod post = new PostMethod(url);
        if (parameters != null) {
            Iterator<?> iter = parameters.entrySet().iterator();
            while (iter.hasNext()) {
                Entry<?, ?> element = (Entry<?, ?>) iter.next();
                post.setParameter(element.getKey().toString(), element
                        .getValue() == null ? "" : element.getValue().toString());
            }
        }
        post.getParams().setContentCharset("UTF-8");
        post.getParams().setParameter(HttpMethodParams.HTTP_CONTENT_CHARSET, "UTF-8");

        String respStr = "";
        try {
            client.executeMethod(post);
            respStr = post.getResponseBodyAsString();
            LOG.info("doPost respStr :" + respStr + " url:" + url);
        } catch (HttpException e) {
            LOG.error("doPost HttpException :" + e.getMessage() + " url:" + url, e);
        } catch (IOException e) {
            LOG.error("doPost IOException :" + e.getMessage() + " url:" + url, e);
        }
        return respStr;
    }

    /**
     * 支持https
     * @param url 请求url
     * @param parameterMap 请求参数
     * @param headersMap  请求头
     * @param resultList  请求方法容器
     * @return
     */
    public static String doPost(String url, Object parameterMap,Map<String, String> headersMap,List<CloseableHttpResponse> resultList) {
        long startTime = System.currentTimeMillis();
        String charset = "utf-8";
        CloseableHttpClient client = null;
        HttpPost httpPost = null;

        try {
            //创建客户端
            client = createHttpClient();
            //创建头与方法
            httpPost = createHttpMethod(new HttpPost(url), headersMap);
            httpPost.setConfig(getRequestConfigDefault());
            //设置参数
            if(parameterMap instanceof Map){
                setParams((Map)parameterMap, charset, httpPost);
            }else if(parameterMap instanceof String){
                setParams((String)parameterMap,headersMap.get("Content-Type"), charset, httpPost);
            }
            //发送请求
            CloseableHttpResponse response = client.execute(httpPost);

            if(resultList != null){
                resultList.add(response);
            }

            int statusCode = response.getStatusLine().getStatusCode();
            String responseText = EntityUtils.toString(response.getEntity(), charset);

            if (statusCode == HttpStatus.SC_OK ) {
                return responseText;
            } else {
                LOG.warn(url + " request failed: " + responseText);
                if (StringUtils.isNotEmpty(responseText) && responseText.length() > 256) {
                    responseText = responseText.substring(0, 256);
                }
                return "httpStatus=" + statusCode + "&httpMessage=" + StringUtils.defaultString(responseText);
            }

        } catch (Exception e) {
            LOG.error(url + " request error: " + e.getMessage(), e);
        } finally {
            LOG.info("execute " + url + " used time: " + (System.currentTimeMillis() - startTime) + "ms");
            if (httpPost != null) {
                httpPost.releaseConnection();
            }
            closeClient(client);
        }

        return "";

    }

    public static String doPost(String url, Map<String, AbstractContentBody> paramMap,Map<String, String> headersMap,List<CloseableHttpResponse> resultList) {
        long startTime = System.currentTimeMillis();
        String charset = "utf-8";
        CloseableHttpClient client = null;
        HttpPost httpPost = null;
        MultipartEntityBuilder multipartEntityBuilder = null;
        HttpEntity reqEntity=null;
        try {
            //创建客户端
            client = createHttpClient();
            //创建头与方法
            httpPost = createHttpMethod(new HttpPost(url), headersMap);
            httpPost.setConfig(getRequestConfigDefault());
            //设置参数
            multipartEntityBuilder=MultipartEntityBuilder.create();
            for(Entry<String, AbstractContentBody> p:paramMap.entrySet()){
                multipartEntityBuilder.addPart(p.getKey(),p.getValue());
            }
            reqEntity=multipartEntityBuilder.build();
            httpPost.setEntity(reqEntity);
            //发送请求
            CloseableHttpResponse response = client.execute(httpPost);
            if(resultList != null){
                resultList.add(response);
            }

            int statusCode = response.getStatusLine().getStatusCode();
            String responseText = EntityUtils.toString(response.getEntity(), charset);

            if (statusCode == HttpStatus.SC_OK ) {
                return responseText;
            } else {
                LOG.warn(url + " request failed: " + responseText);
                if (StringUtils.isNotEmpty(responseText) && responseText.length() > 256) {
                    responseText = responseText.substring(0, 256);
                }
                return "httpStatus=" + statusCode + "&httpMessage=" + StringUtils.defaultString(responseText);
            }

        } catch (Exception e) {
            LOG.error(url + " request error: " + e.getMessage(), e);
        } finally {
            LOG.info("execute " + url + " used time: " + (System.currentTimeMillis() - startTime) + "ms");
            if (httpPost != null) {
                httpPost.releaseConnection();
            }
            closeClient(client);
        }

        return "";

    }




    private static void setParams(Map<String, String> parameterMap, String charset, HttpPost httpPost) throws UnsupportedEncodingException {
        List<BasicNameValuePair> list = new ArrayList<BasicNameValuePair>();
        Set<Entry<String, String>> entryset = parameterMap.entrySet();
        for (Entry<String, String> entry : entryset) {
            list.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        httpPost.setEntity(new UrlEncodedFormEntity(list, charset));
    }

    private static void setParams(String bodyText,String contentType, String charset, HttpPost httpPost) throws UnsupportedEncodingException {
        httpPost.setEntity(new StringEntity(bodyText, ContentType.create(contentType, charset)) );
    }

    /**
     *
     * @param url 请求url
     * @param parameterMap 请求参数
     * @param resultList
     * @return
     */
    public static String doPost(String url, Map<String, String> parameterMap, List<CloseableHttpResponse> resultList) {
        return doPost(url,parameterMap,null,resultList);
    }


    private static <T extends HttpRequestBase> T createHttpMethod(T method, Map<String, String> headers) {

        int connectTimeout = getParameter(headers, "Connection-Timeout", defaultConnectionTimeout);
        method.setConfig(RequestConfig.custom().setConnectionRequestTimeout(connectTimeout).setConnectTimeout(connectTimeout)
                .setSocketTimeout(connectTimeout).build());

        if (headers == null || headers.size() == 0) {
            return method;
        }

        Set<Entry<String, String>> entrySet = headers.entrySet();
        Iterator<Entry<String, String>> iterator = entrySet.iterator();

        while (iterator.hasNext()) {
            Entry<String, String> next = iterator.next();
            String key = next.getKey();
            if ("Http-Status".equals(key) || "Read-Error".equals(key) || "Connection-Timeout".equals(key)) {
                continue;
            }
            String value = next.getValue();
            method.addHeader(key, value);
        }

        return method;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseJsonData(HttpServletRequest request) {
        Map<String, Object> parms = new HashMap<String, Object>();
        BufferedReader br = null;
        try {
            StringBuilder sb = new StringBuilder();
            br = new BufferedReader(new InputStreamReader(request.getInputStream(), "UTF-8"));
            String line = null;
            while ((line = br.readLine()) != null) {
                LOG.info("HttpClientUtil parseJsonData while" + line);
                sb.append(new String(line.getBytes("UTF-8"), "UTF-8"));
            }
            String result = sb.toString();
            if (!StringUtils.isEmpty(result)) {
                Gson gson = new Gson();
                parms = gson.fromJson(result, Map.class);
            }
        } catch (Exception e) {
            LOG.error("HttpClientUtil parseJsonData error:" + e.getLocalizedMessage(), e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    LOG.error(e.getMessage(), e);
                }
            }
        }
        return parms;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> parseXmlData(HttpServletRequest request) {
        Map<String, String> parms = new HashMap<String, String>();
        BufferedReader br = null;
        try {
            StringBuilder sb = new StringBuilder();
            br = new BufferedReader(new InputStreamReader(request.getInputStream(), "UTF-8"));
            String line = null;
            while ((line = br.readLine()) != null) {
                sb.append(new String(line.getBytes("UTF-8"), "UTF-8"));
            }
            String result = sb.toString();
            LOG.info("HttpClientUtil xml" + result);
            if (!StringUtils.isEmpty(result)) {
                parms = XmlUtils.parseToMap(result);
            }
        } catch (Exception e) {
            LOG.error("HttpClientUtil parseXmlData error:" + e.getLocalizedMessage(), e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    LOG.error(e.getMessage(), e);
                }
            }
        }
        return parms;
    }


    /**
     * 该方法不支持https
     * 请求的url @dateType 数据类型 json,xml
     * @param reqUrl
     * @param dateType
     * @return
     */
    public String getResponseMess(String reqUrl, String dateType) {
        long startTime = System.currentTimeMillis();
        String resMess = "";
        HttpClient httpClient;
        GetMethod getMethod = null;
        try {
            httpClient = new HttpClient(connectionManager);

            getMethod = new GetMethod(reqUrl);
            getMethod.getParams().setParameter(HttpMethodParams.USER_AGENT, "FZSservice 1.0");

            LOG.info("request:" + getMethod.getStatusLine());
            getMethod.getParams().setContentCharset(DEFAULT_CHARSET);
            httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(defaultConnectionTimeout);
            httpClient.getHttpConnectionManager().getParams().setSoTimeout(defaultSoTimeout);
            if (dateType != null && "json".equals(dateType)) {
                getMethod.setRequestHeader("Accept", "application/json");
            }
            int statusCode = httpClient.executeMethod(getMethod);
            if (statusCode == HttpStatus.SC_OK) {
                // 读取内容
                resMess = getMethod.getResponseBodyAsString();
            }

        } catch (Exception e) {
            LOG.error(" httpClient error:" + e.getMessage(), e);

        } finally {
            LOG.info("http连接 耗费时间：" + (System.currentTimeMillis() - startTime) + "ms");
            if (getMethod != null) {
                getMethod.releaseConnection();
            }

        }

        return resMess;
    }

    /**
     * param以流的方式写入到请求中
     *
     * @param requestUrl
     * @param method
     * @param param
     * @return
     */
    public String httpRequest(String requestUrl, String method, String param, boolean isJson) {
        StringBuilder temp;
        try {
            URL url = new URL(requestUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            if (isJson) {
                urlConnection.setRequestProperty("Content-Type", "application/json");
            }
            urlConnection.setRequestMethod(method);
            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);
            urlConnection.setUseCaches(false);
            OutputStream outputStream = urlConnection.getOutputStream();
            outputStream.write(param.getBytes("utf-8"));
            outputStream.flush();
            InputStream in = urlConnection.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in, "utf-8"));
            temp = new StringBuilder();
            String line = bufferedReader.readLine();
            while (line != null) {
                temp.append(line).append("\r\n");
                line = bufferedReader.readLine();
            }
            bufferedReader.close();
            int code = urlConnection.getResponseCode();
            if (code != 200) {
                throw new RuntimeException("服务器错误：" + code);
            }
        } catch (Exception e) {
            throw new RuntimeException("服务器错误：" + e.getMessage());
        }
        return temp.toString();
    }


    /**
     * 该方法不支持https
     * @param reqUrl
     * @param dateType
     * @param postParam
     */
    public void post(String reqUrl, String dateType, Map<String, String> postParam) {
        long startTime = System.currentTimeMillis();
        HttpClient httpClient = null;
        PostMethod postMethod = null;
        try {
            httpClient = new HttpClient(connectionManager);
            postMethod = new PostMethod(reqUrl);
            httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(defaultConnectionTimeout);
            httpClient.getHttpConnectionManager().getParams().setSoTimeout(defaultSoTimeout);
            postMethod.getParams().setParameter(HttpMethodParams.USER_AGENT, "FZSservice 1.0");

            if (dateType != null && "json".equals(dateType)) {
                postMethod.setRequestHeader("Accept", "application/json");

            }
            if (postParam != null && !postParam.isEmpty()) {
                for(Entry entry:postParam.entrySet()){
                    postMethod.addParameter(String.valueOf(entry.getKey()),String.valueOf(entry.getValue()));
                }
            }
            // 设置请求参数为utf-8字符集
            postMethod.getParams().setContentCharset("UTF-8");
            LOG.info(Arrays.toString(postMethod.getParameters()));
            LOG.info("uri is:" + postMethod.getURI());
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        } finally {
            LOG.info("http连接 耗费时间：" + (System.currentTimeMillis() - startTime) + "ms");
            if (postMethod != null) {
                postMethod.releaseConnection();
            }
        }

    }

    public static void setCookie(CloseableHttpClient client, Map<String,String> cookiesReceiver){
        try {
            Field field = client.getClass().getDeclaredField("cookieStore");
            field.setAccessible(true);
            List<Cookie> cookieList = ((BasicCookieStore) field.get(client)).getCookies();
            for (Cookie cookie : cookieList) {
                cookiesReceiver.put(cookie.getName(),cookie.getValue());
            }
        } catch (Exception e) {
            LOG.error("获取cookie异常",e);
        }

    }

    public static class HttpsX509TrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
}

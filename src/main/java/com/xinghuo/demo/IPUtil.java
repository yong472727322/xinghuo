package com.xinghuo.demo;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class IPUtil  implements CommandLineRunner {

    private static Logger log = LoggerFactory.getLogger(IPUtil.class);

    //发送给服务器的IP
    private static String host = "";
    //存放当前获取到的IP
    private String currentHost = "";

    /**
     * Creates CloseableHttpClient instance with default configuration.
     */
    private static CloseableHttpClient httpCilent = HttpClients.createDefault();

    @Value("${server.ip}")
    private String server;
    @Value("${vps.server.port}")
    private int port;
    @Value("${vps.ip}")
    private String vps;
    /**
     * 是否验证 连接服务器
     */
    @Value("${vilid.server}")
    private boolean vilidServer;

    /**
     * 是否正在发送IP到服务器
     */
    private boolean isSending = false;

    /**
     * 执行sh脚本
     * @param exec
     * @return
     */
    public String execShell(String exec){
        Runtime runtime = Runtime.getRuntime();
        String charsetName = "UTF-8";
        String result = null;
        try {
            log.info("exec shell is [{}]",exec);
            Process process = runtime.exec(exec);
            InputStream iStream = process.getInputStream();
            InputStreamReader iSReader = new InputStreamReader(iStream,charsetName);
            BufferedReader bReader = new BufferedReader(iSReader);
            String line = null;
            StringBuffer sb = new StringBuffer();
            while ((line = bReader.readLine()) != null) {
                sb.append(line);
            }
            iStream.close();
            iSReader.close();
            bReader.close();
            result  = new String(sb.toString().getBytes(charsetName));
        } catch (Exception e) {
            log.error("exec shell [{}] fail, result is [{}]",exec,e);
        }
        return result;
    }

    private boolean testFlag = false;
    private  SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    //记录近期使用的IP
    private Map<String,Date> map = new LinkedHashMap<>();

    /**
     * 每分钟发送VPS当前IP到服务器
     */
    @Scheduled(cron = "0 0/1 * * * ?")
    public void testVPN() throws ParseException {
        if(flag){
            log.warn("正在启动。。。");
            return;
        }
        if(testFlag){
            log.info("已经在跑-->每分钟发送VPS当前IP到服务器。。。");
        }
        testFlag = true;
        currentHost =  execShell(" /root/only-get-ip.sh ");

        if(!map.containsKey(currentHost)){
            map.put(currentHost,new Date());
            log.info("近期使用的IP数量[{}]",map.size());
        }
        if(map.size() > 20){
            log.info("判断是否服务器故障：近20次不同IP是在近50分钟内产生的。");

            boolean flag = true;
            Date currentDate = new Date();
            for(Map.Entry<String,Date> map1 : map.entrySet()){
                String key = map1.getKey();
                Date value = map1.getValue();
                log.info("IP[{}]获取时间[{}]",key,sdf.format(value));
                if((currentDate.getTime() - value.getTime()) > 50*60*1000 ){
                    // 只要 有一个 IP 是 50分钟之外产生的 就 认为 服务器正常
                    flag = false;
                    break;
                }
            }
            if(flag){
                log.info("近20次不同IP是在近50分钟内产生的，重启服务器");
                execShell("  reboot ");
            }else {
                log.info("近20次不同IP不是在近50分钟内产生的，清除计数。");
                map.clear();
            }
        }
        boolean b = currentHost.equalsIgnoreCase(host);
        log.info("比较当前IP[{}]和发送给服务器的IP[{}]是否一致，结果[{}]",currentHost,host,b);
        if(!b){
            if(vilidIP(currentHost)){
                b = currentHost.equalsIgnoreCase(host);
                if(!b){
                    log.info("当前IP[{}]和发送给服务器的IP[{}]不一致，发送给服务器。",currentHost,host);
                    sendGet("http://"+server+":"+port+"/amazon/recordIP?ip=" + currentHost + "&vps="+vps);
                    if(0 == failCount){
                        host = currentHost;
                    }
                }
            }else {
                log.warn("IP[{}]验证失败，等待5秒，重新拨号",currentHost);
                try {
                    Thread.sleep(5000);
                    execShell("  /root/get-ip.sh ");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        testFlag = false;
    }

    public void getNewHost() {
        try{
            if(isSending){
                log.warn("正在发送IP到服务器。。。");
                return;
            }
            log.info(" start change ip , the old ip is [{}]",host);
            currentHost = execShell("  /root/get-ip.sh ");

            log.info("get ip success , the new ip is [{}]",currentHost);
            boolean vilidIP = vilidIP(currentHost);
            if(!vilidIP){
                log.warn("IP[{}]验证失败，等待30秒，再次进行验证。",currentHost);
                Thread.sleep(30000);
                vilidIP = vilidIP(currentHost);
                if(!vilidIP){
                    log.warn("IP[{}]再次验证失败，等待30秒，最后进行一次验证。",currentHost);
                    Thread.sleep(30000);
                    vilidIP = vilidIP(currentHost);
                    if(!vilidIP){
                        log.warn("IP[{}]连续3次验证失败，使用[stop-network.sh]脚本。",currentHost);
                        currentHost = execShell("  /root/stop-network.sh ");
                        Thread.sleep(5000);
                        log.info("使用[stop-network.sh]脚本 get ip success , the new ip is [{}]",currentHost);
                        vilidIP = vilidIP(currentHost);
                    }
                }
            }
            if(vilidIP){
                sendHost(currentHost);
                if(0 == failCount){
                    host = currentHost;
                }
            }else {
                getNewHost();
            }
        }catch (Exception e){
            log.warn("切换IP出错，再次切换。");
            getNewHost();
        }
    }

    /**
     * 验证IP
     * @param ip
     * @return
     */
    private boolean vilidIP(String ip){
        log.info("判断IP[{}]是否是IP",ip);
        //如果IP的长度  小于 7  或者 大于 20 则认为这个IP不可用
        if(ip.length() > 20 || ip.length() < 7){
            log.info("IP[{}]不是IP，重新换。",ip);
            return false;
        }
        log.info("检测IP[{}]是否可用",ip);
        boolean b = checkIP(ip,"www.amazon.com");
        if(!vilidServer && b){
            log.warn("IP[{}]只验证了[www.amazon.com]。",ip);
            return true;
        }
        boolean b2 = checkIP(ip,server);
        if(b && b2){
            log.warn("IP[{}]可用。",ip);
            return true;
        }else {
            log.warn("IP[{}]不可用。",ip);
            return false;
        }
    }

    private boolean checkIP(String ip,String link){
        log.info("测试与[{}]的连接",link);
        String result = execShell("  /root/check-ip.sh  " + link);
        if(result.contains("%")){
            String[] split = result.split("%");
            String persentStr = split[0];
            int persent = Integer.parseInt(persentStr);
            if(persent <= 30){
                log.warn("IP[{}]丢包率[{}]小于30%，可用",ip,persent);
                return true;
            }
            log.warn("IP[{}]的丢包率为[{}]",ip,persent);
        }
        return false;
    }

    /**
     * 失败次数
     */
    private int failCount = 0;

    int statusCode = 0;
    String resultStr = null;
    /**
     * 发送GET请求
     * @param url
     */
    private void sendGet(String url){

        try {
            isSending = true;
            HttpGet httpGet = new HttpGet(url);
            RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(60000).setConnectTimeout(60000).build();//设置请求和传输超时时间
            httpGet.setConfig(requestConfig);
            HttpResponse execute = httpCilent.execute(httpGet);
            StatusLine statusLine = execute.getStatusLine();
            statusCode = statusLine.getStatusCode();
            resultStr = EntityUtils.toString(execute.getEntity());
            log.info("请求[{}]的响应码是[{}]，返回的结果是[{}]",url,statusCode,resultStr);
            if(200 == statusCode && "true".equalsIgnoreCase(resultStr)){
                //请求成功，失败次数重置为0
                failCount = 0;
            }else if(200 == statusCode && "false".equalsIgnoreCase(resultStr)) {
                log.warn("响应码200，但是结果是[false]，说明保存失败或IP重复，重新拨号");
                failCount ++;
                execShell("  /root/get-ip.sh ");
            }else {
                failCount ++;
            }
        } catch (ConnectTimeoutException e) {
            failCount ++;
            log.warn("ConnectTimeoutException服务器连接超时，等待下次。请求地址[{}]",url);
        } catch (SocketTimeoutException e) {
            failCount ++;
            log.warn("SocketTimeoutException服务器连接超时，等待下次。请求地址[{}]",url);
        } catch (HttpHostConnectException e) {
            failCount ++;
            log.warn("服务器连接不上，可能是服务没开，等待下次。请求地址[{}]",url);
        } catch (IOException e) {
            failCount ++;
            log.error("执行GET请求失败，URL[{}]，原因：[{}]",url,e);
        }finally {
            log.info("失败次数[{}]",failCount);
            if(failCount >= 10){
                //失败超过10次，可能是VPS的问题
                log.warn("失败超过10次，可能是VPS的问题，重新切换IP");
                failCount = 0;
                execShell("  /root/get-ip.sh ");
            }
            isSending = false;
        }
    }

    private void sendHost(String newIp) {
        log.info("send new ip [{}] to server",newIp);
        sendGet("http://"+server+":"+port+"/amazon/saveIP?port=4431&password=password&account="+vps+"&ip=" + newIp+"&vps="+vps);
    }

    private boolean flag = false;

    /**
     * 一启动就重新拨号
     * @param args
     * @throws Exception
     */
    @Override
    public void run(String... args) throws Exception {
        if(StringUtils.isEmpty(vps)){
            log.error("VPS[{}]为空，请配置[vps.ip]的值，系统退出。",vps);
            System.exit(-1);
        }
        log.info("VPS[{}]启动中。。。",vps);
        sendVpsState(true);
        flag = true;
        this.getNewHost();
        flag = false;
        log.info("VPS[{}]启动完成，IP[{}]",vps,host);
    }

    public void sendVpsState(boolean state){
        String url = "http://"+server+":"+port+"/amazon/recordVpsState?vps="+vps+"&state=" + state;
        sendGet(url);
    }
}

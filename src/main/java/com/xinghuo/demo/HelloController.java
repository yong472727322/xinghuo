package com.xinghuo.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("amazon")
public class HelloController {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IPUtil ipUtil;

    @RequestMapping("changVpsIp")
    public void changVpsIp(){
        //发送 当前VPS 状态 给服务器

        ipUtil.getNewHost();
    }


}

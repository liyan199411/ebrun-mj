package com.github.novicezk.midjourney.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by lihy on 2023/5/23.
 */
@Api(tags = "测试接口")
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

	@ApiOperation(value = "测试生成图片")
	@GetMapping("/generateImages")
	public void generateImages(String imgUrl) {
		FileOutputStream fos = null;
		BufferedInputStream bis = null;
		HttpURLConnection httpUrl = null;
		int size = 0;
		byte[] buf = new byte[1024];
		// 创建日期类
		Date date = new Date();
		// 创建文件夹格式化日期
		DateFormat format1 = new SimpleDateFormat("yyyyMMdd");
		String pan = "/tmp/ebrunmj/";
		//拼接盘符
		String path=pan+format1.format(date)+"/";
		System.out.println("上传文件路径--->"+path);
		File f=new File(path);
		// 判断上传的文件是否存在
		//判断是否是文件夹
		if(!f.isDirectory() && !f.exists()){
			//创建单层目录
			// f.mkdir();
			// 创建多层目录
			f.mkdirs();
		}
		String filePath = path + System.currentTimeMillis()+".png";
		File file = new File(filePath);
		// 判断上传的文件是否存在
		if (file.exists()) {
			// 存在则删除
			file.delete();
		}
		try{
			URL url = new URL(imgUrl);
			httpUrl = (HttpURLConnection)url.openConnection();
			httpUrl.connect();
			bis = new BufferedInputStream(httpUrl.getInputStream());
			fos = new FileOutputStream(file);
			while ((size = bis.read(buf)) != -1)
			{
				fos.write(buf, 0, size);
			}
			fos.flush();
			fos.close();
			bis.close();
			System.out.println("生成图片成功路径：--->"+filePath);
		}catch (Exception e){
			System.out.println("读取文件链接报错图片路径：--->"+imgUrl);
			e.printStackTrace();
		}
	}
}

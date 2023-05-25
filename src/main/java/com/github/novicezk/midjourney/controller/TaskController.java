package com.github.novicezk.midjourney.controller;

import cn.hutool.core.bean.BeanUtil;
import com.github.novicezk.midjourney.service.TaskStoreService;
import com.github.novicezk.midjourney.support.Task;
import com.github.novicezk.midjourney.support.TaskNotify;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import java.util.List;

@Api(tags = "任务查询")
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskController {
	private final TaskStoreService taskStoreService;

	@ApiOperation(value = "列出所有任务信息")
	@GetMapping("/list")
	public List<Task> listTask() {
		return this.taskStoreService.listTask();
	}

	@ApiOperation(value = "列出指定id任务信息")
	@GetMapping("/{id}/fetch")
	public TaskNotify getTask(@ApiParam(value = "任务id") @PathVariable String id) {
		Task task = this.taskStoreService.getTask(id);
		System.out.println("任务回调数据, task:"+ task);
		TaskNotify taskNotify = new TaskNotify();
		if(task != null){
			BeanUtil.copyProperties(task,taskNotify);
			if(StringUtils.isNotBlank(task.getImageUrl())){
				String imgUrl = task.getImageUrl();
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
				String formatPath = format1.format(date)+"/";
				//拼接盘符
				String path=pan+formatPath;
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
				String imgPath = System.currentTimeMillis() + ".png";
				String filePath = path + formatPath+imgPath;
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
					String imgFilePath = "http://54.67.79.231/ebrunimgs/"+formatPath+imgPath;
					System.out.println("生成图片成功路径：--->"+filePath);
					System.out.println("生成图片可访问的路径：--->"+imgFilePath);
					taskNotify.setLocalImageUrl(imgFilePath);
				}catch (Exception e){
					System.out.println("读取文件链接报错图片路径：--->"+imgUrl);
					e.printStackTrace();
				}
			}
		}
		return taskNotify;
	}

}

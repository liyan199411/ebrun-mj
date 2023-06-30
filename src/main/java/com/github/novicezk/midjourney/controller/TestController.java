package com.github.novicezk.midjourney.controller;

import com.github.novicezk.midjourney.service.TaskService;
import com.github.novicezk.midjourney.support.Task;
import com.github.novicezk.midjourney.support.TaskNotify;
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

	private final TaskService taskService;

	@ApiOperation(value = "测试生成图片")
	@GetMapping("/generateImages")
	public TaskNotify generateImages(String imgUrl) {
		Task task = new Task();
		task.setImageUrl(imgUrl);
		return taskService.uploadImgUrl(task);
	}

	@ApiOperation(value = "测试生成本地图片")
	@GetMapping("/localityImg")
	public TaskNotify localityImg(String imgUrl) {
		Task task = new Task();
		task.setImageUrl(imgUrl);
		return taskService.localityImg(task);
	}
}

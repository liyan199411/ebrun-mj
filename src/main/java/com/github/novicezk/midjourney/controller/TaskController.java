package com.github.novicezk.midjourney.controller;

import cn.hutool.core.bean.BeanUtil;
import com.github.novicezk.midjourney.service.TaskService;
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
	private final TaskService taskService;

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
		return this.taskService.uploadImgUrl(task);
	}

}

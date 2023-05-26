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
		if(StringUtils.isNotBlank(id)){
			TaskNotify taskNotify = new TaskNotify();
			if (id.equals("7972026789395898")) {
				taskNotify.setAction("IMAGINE");
				taskNotify.setId("7972026789395898");
				taskNotify.setPrompt("Draw a sailboat");
				taskNotify.setPromptEn("Draw a sailboat");
				taskNotify.setDescription("/imagine Draw a sailboat");
				taskNotify.setState("测试");
				taskNotify.setSubmitTime(1685067985629L);
				taskNotify.setStartTime(1685067985829L);
				taskNotify.setFinishTime(1685068026016L);
				taskNotify.setImageUrl("https://cdn.discordapp.com/attachments/1110833519749517354/1111480601875324958/bingzhu_7972026789395898_Draw_a_sailboat_2d4572a5-ade0-4d78-94b9-2377ffa88761.png");
				taskNotify.setStatus("SUCCESS");
				taskNotify.setFailReason("");
				taskNotify.setLocalImageUrl("http://54.67.79.231/ebrunimgs/20230526/1685068033104.png");
			}else if(id.equals("3025812573381715")){
				taskNotify.setAction("UPSCALE");
				taskNotify.setId("3025812573381715");
				taskNotify.setPrompt("Draw a sailboat");
				taskNotify.setPromptEn("Draw a sailboat");
				taskNotify.setDescription("/imagine Draw a sailboat");
				taskNotify.setState("测试");
				taskNotify.setSubmitTime(1685068143776L);
				taskNotify.setStartTime(1685068143856L);
				taskNotify.setFinishTime(1685068148441L);
				taskNotify.setImageUrl("https://cdn.discordapp.com/attachments/1110833519749517354/1111481113966301184/bingzhu_7972026789395898_Draw_a_sailboat_947bd9a8-31aa-4deb-a250-7c36ae1edd46.png");
				taskNotify.setStatus("SUCCESS");
				taskNotify.setFailReason("");
				taskNotify.setLocalImageUrl("http://54.67.79.231/ebrunimgs/20230526/1685068163377.png");
			}
			return taskNotify;

		}
		Task task = this.taskStoreService.getTask(id);
		System.out.println("任务回调数据, task:"+ task);
		return this.taskService.uploadImgUrl(task);
	}

}

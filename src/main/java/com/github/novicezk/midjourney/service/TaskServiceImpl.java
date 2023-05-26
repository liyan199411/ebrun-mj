package com.github.novicezk.midjourney.service;

import cn.hutool.core.bean.BeanUtil;
import com.github.novicezk.midjourney.ProxyProperties;
import com.github.novicezk.midjourney.enums.TaskStatus;
import com.github.novicezk.midjourney.result.Message;
import com.github.novicezk.midjourney.support.Task;
import com.github.novicezk.midjourney.support.TaskCondition;
import com.github.novicezk.midjourney.support.TaskNotify;
import com.github.novicezk.midjourney.util.MimeTypeUtils;
import eu.maxschuster.dataurl.DataUrl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

@Slf4j
@Service
public class TaskServiceImpl implements TaskService {
	@Resource
	private TaskStoreService taskStoreService;
	@Resource
	private DiscordService discordService;
	@Resource
	private NotifyService notifyService;

	private final ThreadPoolTaskExecutor taskExecutor;
	private final List<Task> runningTasks;

	public TaskServiceImpl(ProxyProperties properties) {
		ProxyProperties.TaskQueueConfig queueConfig = properties.getQueue();
		this.runningTasks = Collections.synchronizedList(new ArrayList<>(queueConfig.getCoreSize() * 2));
		this.taskExecutor = new ThreadPoolTaskExecutor();
		this.taskExecutor.setCorePoolSize(queueConfig.getCoreSize());
		this.taskExecutor.setMaxPoolSize(queueConfig.getCoreSize());
		this.taskExecutor.setQueueCapacity(queueConfig.getQueueSize());
		this.taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		this.taskExecutor.setThreadNamePrefix("TaskQueue-");
		this.taskExecutor.initialize();
	}

	@Override
	public Task getTask(String id) {
		return this.runningTasks.stream().filter(t -> id.equals(t.getId())).findFirst().orElse(null);
	}

	@Override
	public Stream<Task> findTask(TaskCondition condition) {
		return this.runningTasks.stream().filter(condition);
	}

	@Override
	public Message<String> submitImagine(Task task) {
		return submitTask(task, () -> {
			Message<Void> result = this.discordService.imagine(task.getFinalPrompt());
			checkAndWait(task, result);
		});
	}

	@Override
	public Message<String> submitUpscale(Task task, String targetMessageId, String targetMessageHash, int index) {
		return submitTask(task, () -> {
			Message<Void> result = this.discordService.upscale(targetMessageId, index, targetMessageHash);
			checkAndWait(task, result);
		});
	}

	@Override
	public Message<String> submitVariation(Task task, String targetMessageId, String targetMessageHash, int index) {
		return submitTask(task, () -> {
			Message<Void> result = this.discordService.variation(targetMessageId, index, targetMessageHash);
			checkAndWait(task, result);
		});
	}

	@Override
	public Message<String> submitDescribe(Task task, DataUrl dataUrl) {
		return submitTask(task, () -> {
			String taskFileName = task.getId() + "." + MimeTypeUtils.guessFileSuffix(dataUrl.getMimeType());
			Message<String> uploadResult = this.discordService.upload(taskFileName, dataUrl);
			if (uploadResult.getCode() != Message.SUCCESS_CODE) {
				task.setFinishTime(System.currentTimeMillis());
				task.setFailReason(uploadResult.getDescription());
				changeStatusAndNotify(task, TaskStatus.FAILURE);
				return;
			}
			String finalFileName = uploadResult.getResult();
			Message<Void> result = this.discordService.describe(finalFileName);
			checkAndWait(task, result);
		});
	}

	@Override
	public TaskNotify uploadImgUrl(Task task) {
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
				String filePath = path +imgPath;
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
					taskNotify.setFailReason("操作成功");
				}catch (Exception e){
					System.out.println("读取文件链接报错图片路径：--->"+imgUrl);
					e.printStackTrace();
				}
			}else if(task.getStatus().equals(TaskStatus.SUCCESS) && StringUtils.isBlank(task.getImageUrl())){
				taskNotify.setFailReason("生成图片成功，无图片链接");
			}else {
				if (StringUtils.isBlank(taskNotify.getFailReason())) {
					if (task.getStatus().equals(TaskStatus.NOT_START)) {
						taskNotify.setFailReason("未启动");
					} else if (task.getStatus().equals(TaskStatus.SUBMITTED)) {
						taskNotify.setFailReason("已提交处理");
					} else if (task.getStatus().equals(TaskStatus.IN_PROGRESS)) {
						taskNotify.setFailReason("执行中");
					} else if (task.getStatus().equals(TaskStatus.FAILURE)) {
						taskNotify.setFailReason("图片生成失败");
					}
				}
			}
		}
		return taskNotify;
	}

	private Message<String> submitTask(Task task, Runnable runnable) {
		this.taskStoreService.saveTask(task);
		int size;
		try {
			size = this.taskExecutor.getThreadPoolExecutor().getQueue().size();
			this.taskExecutor.execute(() -> {
				task.setStartTime(System.currentTimeMillis());
				this.runningTasks.add(task);
				try {
					this.taskStoreService.saveTask(task);
					runnable.run();
				} finally {
					this.runningTasks.remove(task);
				}
			});
		} catch (RejectedExecutionException e) {
			this.taskStoreService.deleteTask(task.getId());
			return Message.failure("队列已满，请稍后尝试");
		}
		if (size == 0) {
			return Message.success(task.getId());
		} else {
			return Message.success(Message.WAITING_CODE, "排队中，前面还有" + size + "个任务", task.getId());
		}
	}

	private void checkAndWait(Task task, Message<Void> result) {
		if (result.getCode() != Message.SUCCESS_CODE) {
			task.setFinishTime(System.currentTimeMillis());
			task.setFailReason(result.getDescription());
			changeStatusAndNotify(task, TaskStatus.FAILURE);
			return;
		}
		changeStatusAndNotify(task, TaskStatus.SUBMITTED);
		do {
			try {
				task.sleep();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			changeStatusAndNotify(task, task.getStatus());
		} while (task.getStatus() == TaskStatus.IN_PROGRESS);
		log.debug("task finished, id: {}, status: {}", task.getId(), task.getStatus());
	}

	private void changeStatusAndNotify(Task task, TaskStatus status) {
		task.setStatus(status);
		this.taskStoreService.saveTask(task);
		this.notifyService.notifyTaskChange(task);
	}

}
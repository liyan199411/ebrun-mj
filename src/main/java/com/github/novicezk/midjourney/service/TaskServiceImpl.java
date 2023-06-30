package com.github.novicezk.midjourney.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.novicezk.midjourney.ProxyProperties;
import com.github.novicezk.midjourney.enums.TaskStatus;
import com.github.novicezk.midjourney.result.Message;
import com.github.novicezk.midjourney.support.Task;
import com.github.novicezk.midjourney.support.TaskCondition;
import com.github.novicezk.midjourney.support.TaskNotify;
import com.github.novicezk.midjourney.util.MimeTypeUtils;
import com.github.novicezk.midjourney.util.OkHttpUtils;
import eu.maxschuster.dataurl.DataUrl;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
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
				System.out.println("图片url="+task.getImageUrl());
				String imgUrl = task.getImageUrl();
				String filename = imgUrl.substring(imgUrl.lastIndexOf("/") + 1);
				String filenameType = imgUrl.substring(imgUrl.lastIndexOf(".") + 1);
				String filePath = generateFileName();
				String basePath = "/tmp/ebrunmj/";
				String orgFilePath = filePath + "_org" + "." + filenameType;
				String bigFilePath = filePath + "_big" + "."+ filenameType;
				String smallFilePath = filePath + "_small" + "."+ filenameType;
				String orgPath = basePath +orgFilePath;
				String bigPath = basePath + bigFilePath;
				String smallPath = basePath + smallFilePath;
				String imgPathUrl = "https://imgs.ebrun.com/midjourney/";

				File localFile = new File(orgPath);
				File parent = localFile.getParentFile();
				if (parent != null && !parent.exists()) {
					boolean mkdirs = parent.mkdirs();
					if (!mkdirs) {
						System.out.println(parent.getPath() + "创建失败，请检查是否有权限");
					}
				}
				try {
					imgFileGenerate(imgUrl,orgPath);
					System.out.println("原图文件保存路径----->>" + orgPath);
					taskNotify.setLocalImageUrlOrigin(imgPathUrl+orgFilePath);
					System.out.println("生成原图可访问的路径：--->"+imgPathUrl+orgFilePath);

					BufferedImage bufferedImage = ImageIO.read(localFile);
					if (bufferedImage != null){
						Integer width = bufferedImage.getWidth();
						Integer height = bufferedImage.getHeight();
						if (width > 1200){
							DecimalFormat df = new DecimalFormat("0.000");
							String num = df.format((float)width/height);
							int parseInt = Integer.parseInt(new DecimalFormat("0").format(Math.ceil((float) 1200 / Float.parseFloat(num))));
							//scaleImageWithParams(orgPath,bigPath,1200,parseInt);
							Thumbnails.of(orgPath).forceSize(1200,parseInt).toFile(bigPath);
							System.out.println("压缩后的大图保存路径----->>" + bigPath);
						}else {
							//Thumbnails.of(orgPath).forceSize(width,height).toFile(bigPath);
							//scaleImageWithParams(orgPath, bigPath,width,height);
							imgFileGenerate(imgUrl,bigPath);
							System.out.println("大图保存路径----->>" + bigPath);
						}
						taskNotify.setLocalImageUrlBig(imgPathUrl+bigFilePath);
						System.out.println("生成大图可访问的路径：--->"+imgPathUrl+bigFilePath);

						if (width > 450) {
							DecimalFormat df = new DecimalFormat("0.000");
							String num = df.format((float)width/height);
							int parseInt = Integer.parseInt(new DecimalFormat("0").format(Math.ceil((float) 450 / Float.parseFloat(num))));
							Thumbnails.of(orgPath).forceSize(450,parseInt).toFile(smallPath);
							//scaleImageWithParams(orgPath,smallPath,450,parseInt);
							System.out.println("压缩后的小图保存路径----->>" + smallPath);
						}else {
							Thumbnails.of(orgPath).forceSize(width,height).toFile(smallPath);
							//scaleImageWithParams(orgPath, smallPath,width,height);
							System.out.println("小图保存路径----->>" + smallPath);
						}
						taskNotify.setLocalImageUrl(imgPathUrl+smallFilePath);
						System.out.println("生成小图可访问的路径：--->"+imgPathUrl+smallFilePath);
					}else {
						System.out.println("压缩大图小图失败");
					}
					taskNotify.setFailReason("操作成功");
				}catch (Exception e) {
					System.out.println("读取文件链接报错图片路径：--->"+imgUrl);
					e.printStackTrace();
				}
			}else if(task.getStatus().equals(TaskStatus.SUCCESS) && StringUtils.isBlank(task.getImageUrl())){
				taskNotify.setFailReason("生成图片成功，Mj无图片链接");
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

	@Override
	public TaskNotify localityImg(Task task) {
		TaskNotify taskNotify = new TaskNotify();
		if(task != null){
			BeanUtil.copyProperties(task,taskNotify);
			if(StringUtils.isNotBlank(task.getImageUrl())){
				log.info("图片url="+task.getImageUrl());
				String imgUrl = task.getImageUrl();
				String filenameType = imgUrl.substring(imgUrl.lastIndexOf(".") + 1);
				String filePath = generateFileName()+"aaa";

				String basePath = "/tmp/ebrunmj/";
				String orgFilePath = filePath + "_org" + "." + filenameType;
				String bigFilePath = filePath + "_big" + "."+ filenameType;
				String smallFilePath = filePath + "_small" + "."+ filenameType;
				String orgPath = basePath +orgFilePath;
				String bigPath = basePath + bigFilePath;
				String smallPath = basePath + smallFilePath;
				String imgPathUrl = "https://imgs.ebrun.com/midjourney/";

				File localFile = new File(orgPath);
				File parent = localFile.getParentFile();
				if (parent != null && !parent.exists()) {
					boolean mkdirs = parent.mkdirs();
					if (!mkdirs) {
						System.out.println(parent.getPath() + "创建失败，请检查是否有权限");
					}
				}
				try {
					imgFileGenerate(imgUrl,orgPath);
					System.out.println("原图文件保存路径----->>" + orgPath);

					System.out.println("生成原图可访问的路径：--->"+imgPathUrl+orgFilePath);

					Map<String,Object> par = new HashMap<>();
					par.put("filePath",filePath);
					par.put("imgPathUrl",imgPathUrl);
					JSONObject parJson = new JSONObject(par);
					String parStr = parJson.toJSONString();
					HashMap<String,String> header = new HashMap<>();
					header.put("Content-Type","application/x-www-form-urlencoded");
					String link = "https://oa.ebrun.com/documentonline/api/midjourney";
					String result = OkHttpUtils.getInstance().doPostJson(link, parStr, header);
					log.info("mj图片生成本地结果:"+result);
					if (StringUtils.isNotBlank(result)) {
						JSONObject jsonObject = JSONObject.parseObject(result);

						String state = Convert.toStr(jsonObject.get("state"), "");
						String info = Convert.toStr(jsonObject.get("info"), "");
						String orgFilePathLocal = Convert.toStr(jsonObject.get("orgFilePath"), "");
						if (StringUtils.isNotBlank(orgFilePathLocal)) {
							if (orgFilePathLocal.equals(orgFilePath)) {
								try{
									if (localFile.isFile()) {
										log.info("删除mj图片已生成的图片，路径:"+orgFilePath);
										localFile.delete();
									}
								}catch (Exception e){
									e.printStackTrace();
								}
							}

							taskNotify.setLocalImageUrlOrigin(imgPathUrl+orgFilePathLocal);
							String bigFilePathLocal = Convert.toStr(jsonObject.get("bigFilePath"), "");
							String smallFilePathLocal = Convert.toStr(jsonObject.get("smallFilePath"), "");

							taskNotify.setLocalImageUrlBig(imgPathUrl+bigFilePathLocal);
							System.out.println("生成大图可访问的路径：--->"+imgPathUrl+bigFilePath);

							taskNotify.setLocalImageUrl(imgPathUrl+smallFilePathLocal);
							System.out.println("生成小图可访问的路径：--->"+imgPathUrl+smallFilePath);
							log.info("本地原图结果:"+orgFilePathLocal);
							log.info("本地大图结果:"+bigPath);
							log.info("本地小图结果:"+smallPath);
							taskNotify.setFailReason("操作成功");
						}else {
							taskNotify.setFailReason("生成图片转存本地失败，结果："+result);
						}

					}
				}catch (Exception e) {
					System.out.println("读取文件链接报错图片路径：--->"+imgUrl);
					e.printStackTrace();
				}
			}else if(task.getStatus().equals(TaskStatus.SUCCESS) && StringUtils.isBlank(task.getImageUrl())){
				taskNotify.setFailReason("生成图片成功，Mj无图片链接");
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

	private String imgFileGenerate(String imgUrl,String path){

		try{
			int size = 0;
			byte[] buf = new byte[1024];
			URL urlC = new URL(imgUrl);
			HttpURLConnection httpUrl = (HttpURLConnection)urlC.openConnection();
			httpUrl.connect();
			BufferedInputStream bis = new BufferedInputStream(httpUrl.getInputStream());
			FileOutputStream fos = new FileOutputStream(path);
			while ((size = bis.read(buf)) != -1)
			{
				fos.write(buf, 0, size);
			}
			fos.flush();
			fos.close();
			bis.close();
		}catch (Exception e){
			e.printStackTrace();
		}
		System.out.println("文件保存路径----->>" + path);
		return path;
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

	public String downloadImage(String url) {

		if (StringUtils.isNotBlank(url)) {
			String filename = url.substring(url.lastIndexOf("/") + 1);
			String filenameType = url.substring(url.lastIndexOf(".") + 1);
			filename = filename.substring(0, filename.indexOf(".", 3));
			String filePath = generateFileName();
			String folderName = "/imageFolder/";
			String basePath = folderName;
			String orgPath = basePath + filePath + "_org" + "."+ filenameType;
			String bigPath = basePath + filePath + "_big" + "."+ filenameType;
			String smallPath = basePath + filePath + "_small" + "."+ filenameType;
			String normalPath = folderName + filePath  + "."+ filenameType;
			File localFile = new File(orgPath);
			File parent = localFile.getParentFile();
			if (parent != null && !parent.exists()) {
				boolean mkdirs = parent.mkdirs();
				if (!mkdirs) {
					System.out.println(parent.getPath() + "创建失败，请检查是否有权限");
					return null;
				}
			}

			try {
				int size = 0;
				byte[] buf = new byte[1024];
				URL urlC = new URL(url);
				HttpURLConnection httpUrl = (HttpURLConnection)urlC.openConnection();
				httpUrl.connect();
				BufferedInputStream bis = new BufferedInputStream(httpUrl.getInputStream());
				FileOutputStream fos = new FileOutputStream(orgPath);
				while ((size = bis.read(buf)) != -1)
				{
					fos.write(buf, 0, size);
				}
				fos.flush();
				fos.close();
				bis.close();
				System.out.println("原图文件保存路径----->>" + orgPath);

				BufferedImage bufferedImage = ImageIO.read(localFile);
				if (bufferedImage != null){
					Integer width = bufferedImage.getWidth();
					Integer height = bufferedImage.getHeight();
					if (width > 1200){
						DecimalFormat df = new DecimalFormat("0.000");
						String num = df.format((float)width/height);
						int parseInt = Integer.parseInt(new DecimalFormat("0").format(Math.ceil((float) 1200 / Float.parseFloat(num))));
//                        scaleImageWithParams(orgPath,bigPath,1200,parseInt);
						Thumbnails.of(orgPath).forceSize(1200,parseInt).toFile(bigPath);
						System.out.println("压缩后的大图保存路径----->>" + bigPath);
					}else {
						Thumbnails.of(orgPath).forceSize(width,height).toFile(bigPath);
//                        scaleImageWithParams(orgPath, bigPath,width,height);
						System.out.println("大图保存路径----->>" + bigPath);
					}

					if (width > 450) {
						DecimalFormat df = new DecimalFormat("0.000");
						String num = df.format((float)width/height);
						int parseInt = Integer.parseInt(new DecimalFormat("0").format(Math.ceil((float) 450 / Float.parseFloat(num))));
						Thumbnails.of(orgPath).forceSize(450,parseInt).toFile(smallPath);
//                        scaleImageWithParams(orgPath,smallPath,450,parseInt);
						System.out.println("压缩后的小图保存路径----->>" + smallPath);
					}else {
						Thumbnails.of(orgPath).forceSize(width,height).toFile(smallPath);
//                        scaleImageWithParams(orgPath, smallPath,width,height);
						System.out.println("小图保存路径----->>" + smallPath);
					}
				}else {
					System.out.println("压缩大图小图失败");
				}
				return normalPath;
			}catch (Exception e) {
				System.out.println("上传图片失败。。。。。。");
				e.printStackTrace();
				return null;
			}
		}

		return null;
	}

	public static String generateFileName() {
		StringBuffer sb = new StringBuffer();
		Calendar c = Calendar.getInstance();
		c.setTime(new Date());
		int temp = 0;
		// 年月目录
		sb.append(c.get(Calendar.YEAR));
		temp = c.get(Calendar.MONTH) + 1;
		if (temp < 10)
			sb.append("0");
		sb.append(temp);
		sb.append("/");
		// 年月日目录
		sb.append(c.get(Calendar.YEAR));
		temp = c.get(Calendar.MONTH) + 1;
		if (temp < 10)
			sb.append("0");
		sb.append(temp);
		temp = c.get(Calendar.DAY_OF_MONTH);
		if (temp < 10)
			sb.append("0");
		sb.append(temp);
		sb.append("/");
		// 随机数当前毫秒文件名
		Random rnd = new Random();
		temp = rnd.nextInt(999);
		if (temp < 10)
			sb.append("00");
		else if (temp < 100 && temp > 10)
			sb.append("0");
		sb.append(temp);
		sb.append(Long.toString(c.getTimeInMillis())
				+ Math.round(Math.random() * 5));
		return sb.toString();
	}

	public static void scaleImageWithParams(String sourceImagePath,
									 String destinationPath, int width, int height) {

		try {
			BufferedImage bufferedImage = ImageIO.read(FileUtil.file(sourceImagePath));
			ImgUtil.write(ImgUtil.scale(bufferedImage,width,height), FileUtil.file(destinationPath));
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println(e);
		}
	}

}
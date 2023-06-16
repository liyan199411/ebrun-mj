package com.github.novicezk.midjourney.service.store;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.collection.ListUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.novicezk.midjourney.service.TaskStoreService;
import com.github.novicezk.midjourney.support.Task;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;

@Slf4j
public class InMemoryTaskStoreServiceImpl implements TaskStoreService {
	private final TimedCache<String, Task> taskMap;

	public InMemoryTaskStoreServiceImpl(Duration timeout) {
		log.info("时间毫秒: {}",timeout.toMillis());
		this.taskMap = CacheUtil.newTimedCache(timeout.toMillis());
	}

	@Override
	public void saveTask(Task task) {
		this.taskMap.put(task.getId(), task);
	}

	@Override
	public void deleteTask(String key) {
		this.taskMap.remove(key);
	}

	@Override
	public Task getTask(String key) {
		log.info("通过id：{}",key);
		Task task = this.taskMap.get(key);
		String jsonString = JSONObject.toJSONString(task);
		log.info("通过id获取, task数据：{}",jsonString);
		return task;
	}

	@Override
	public List<Task> listTask() {
		return ListUtil.toList(this.taskMap.iterator());
	}

}

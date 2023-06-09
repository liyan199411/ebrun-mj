package com.github.novicezk.midjourney.support;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.novicezk.midjourney.enums.Action;
import com.github.novicezk.midjourney.enums.TaskStatus;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Created by lihy on 2023/5/25.
 */
@Data
public class TaskNotify implements Serializable {

	@Serial
	private static final long serialVersionUID = -674915748204390655L;

	private String action;
	private String id;
	private String prompt;
	private String promptEn;

	private String description;
	private String state;
	private Long submitTime;
	private Long startTime;
	private Long finishTime;
	private String imageUrl;
	private String status;
	private String failReason;
	private String localImageUrl;//小图
	private String localImageUrlBig;//大图
	private String localImageUrlOrigin;//原图


	@JsonIgnore
	private final transient Object lock = new Object();

	public void sleep() throws InterruptedException {
		synchronized (this.lock) {
			this.lock.wait();
		}
	}

	public void awake() {
		synchronized (this.lock) {
			this.lock.notifyAll();
		}
	}
}

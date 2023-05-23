package com.github.novicezk.midjourney.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

/**
 * Created by lihy on 2023/5/23.
 */
@Api(tags = "任务回调")
@RestController
@RequestMapping("/notify")
@RequiredArgsConstructor
public class NotifyController {

	@ApiOperation(value = "任务回调地址")
	@PostMapping("/submit")
	public void submitReturn(HttpServletResponse httpResponse) {
		System.out.println("任务回调："+httpResponse);
	}
}


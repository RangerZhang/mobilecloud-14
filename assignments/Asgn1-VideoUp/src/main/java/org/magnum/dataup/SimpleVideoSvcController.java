/*
 * 
 * Copyright 2014 Jules White
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.magnum.dataup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;
import org.magnum.dataup.model.VideoStatus.VideoState;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.http.Multipart;
import retrofit.http.Part;
import retrofit.http.Path;
import retrofit.http.Streaming;
import retrofit.mime.TypedFile;

@Controller
public class SimpleVideoSvcController {
	
	// generate a global unique Id
	private static final AtomicLong currentId = new AtomicLong(0L);
	private HashMap<Long,Video> videos = new HashMap<Long, Video>();
  	public Video save(Video entity) {
		checkAndSetId(entity);
		videos.put(entity.getId(), entity);
		return entity;
	}
	private void checkAndSetId(Video entity) {
		if(entity.getId() == 0){
			entity.setId(currentId.incrementAndGet());
		}
	}
	

	@RequestMapping(value="/video", method=RequestMethod.POST)
	public @ResponseBody Video addVideo(@RequestBody Video v){
		//generate an Id and an URL
		save(v);
		v.setDataUrl(getDataUrl(v.getId()));		
		return v;
	}
	
	@RequestMapping(value="/video", method=RequestMethod.GET)
	public @ResponseBody Collection<Video> getVideoList(){
		return videos.values();
	}
	
	private static VideoFileManager videoFileManager;

	@RequestMapping(value="/video/{id}/data", method=RequestMethod.POST)
	public @ResponseBody VideoStatus setVideoData(@PathVariable("id") long id, @RequestParam("data") MultipartFile videoData, HttpServletResponse response) throws IOException{
		VideoStatus status = new VideoStatus(VideoState.READY);
		//don't have the video: return 404 - the client should receive a 404 error and throw an exception
		if (!videos.containsKey(id)) {
		//	response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return status; // if the id is invalid, the status won't be checked by the client
		}	

		videoFileManager = (null == videoFileManager) ? VideoFileManager.get() : videoFileManager;
		videoFileManager.saveVideoData(videos.get(id), videoData.getInputStream());
		//succesful: status.state == VideoState.READY				
		return status;
	}
	
	@RequestMapping(value="/video/{id}/data", method=RequestMethod.GET)
	HttpServletResponse getData(@PathVariable(value="id") long id, HttpServletResponse response) throws IOException{
		//don't have the data: response.state == 404 
		videoFileManager = (null == videoFileManager) ? VideoFileManager.get() : videoFileManager;
		if (!videos.containsKey(id) || !videoFileManager.hasVideoData(videos.get(id))) {
			response.sendError(404);
			return response;
		}
		
		//successful: response.state == 200
		videoFileManager.copyVideoData(videos.get(id), response.getOutputStream());
		response.setStatus(200);
		return response;
	}
	
	
	
    private String getDataUrl(long videoId){
        String url = getUrlBaseForLocalServer() + "/video/" + videoId + "/data";
        return url;
    }
    // figure out the address of my "server"
 	private String getUrlBaseForLocalServer() {
	   HttpServletRequest request = 
	       ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
	   String base = "http://"+request.getServerName() 
	      + ((request.getServerPort() != 80) ? ":"+request.getServerPort() : "");
	   return base;
	}
	
}

package run_ffmpeg;

import java.util.List;

import com.google.gson.Gson;

public class FFmpegProbeFrames
{
	List< FFmpegProbeFrame > frames = null ;
	
	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}

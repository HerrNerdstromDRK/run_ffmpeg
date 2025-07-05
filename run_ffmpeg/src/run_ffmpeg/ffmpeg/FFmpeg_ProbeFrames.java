package run_ffmpeg.ffmpeg;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class FFmpeg_ProbeFrames
{
	public List< FFmpeg_ProbeFrame > frames = null ;
	
	public String toString()
	{
		GsonBuilder builder = new GsonBuilder(); 
		builder.setPrettyPrinting(); 
		Gson gson = builder.create();
		final String loginRequestJson = gson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}

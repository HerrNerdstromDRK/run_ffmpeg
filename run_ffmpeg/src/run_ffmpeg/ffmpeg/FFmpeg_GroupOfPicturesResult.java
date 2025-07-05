package run_ffmpeg.ffmpeg;

import java.util.List;

import com.google.gson.Gson;

public class FFmpeg_GroupOfPicturesResult
{
	public FFmpeg_GroupOfPicturesResult()
	{}

	public List< FFmpeg_ProbeFrames > frames = null ;

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}

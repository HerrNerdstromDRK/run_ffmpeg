package run_ffmpeg;

import java.util.List;

import com.google.gson.Gson;

public class FFmpegGroupOfPicturesResult
{
	public FFmpegGroupOfPicturesResult()
	{}

	public List< FFmpegProbeFrames > frames = null ;

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}

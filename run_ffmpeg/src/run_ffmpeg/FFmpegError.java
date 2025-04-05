package run_ffmpeg;

import com.google.gson.Gson;

public class FFmpegError
{
	public int code;
	public String string;

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}

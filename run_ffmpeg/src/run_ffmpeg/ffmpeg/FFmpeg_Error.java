package run_ffmpeg.ffmpeg;

import com.google.gson.Gson;

public class FFmpeg_Error
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

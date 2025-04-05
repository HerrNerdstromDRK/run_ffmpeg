package run_ffmpeg;

import com.google.gson.Gson;

public class FFmpegChapterTag
{
	public String title;

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}

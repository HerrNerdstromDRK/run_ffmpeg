package run_ffmpeg.ffmpeg;

import com.google.gson.Gson;

public class FFmpeg_ChapterTag
{
	public String title;

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}

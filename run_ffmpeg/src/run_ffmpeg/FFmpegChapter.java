package run_ffmpeg;

import com.google.gson.Gson;

public class FFmpegChapter
{
	public int id;
	public String time_base;
	public long start;
	public String start_time;
	public long end;
	public String end_time;
	public FFmpegChapterTag tags;

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}

}
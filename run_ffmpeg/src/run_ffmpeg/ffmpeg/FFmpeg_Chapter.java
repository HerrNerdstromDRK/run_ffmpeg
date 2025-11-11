package run_ffmpeg.ffmpeg;

import com.google.gson.Gson;

public class FFmpeg_Chapter
{
	public int id;
	public String time_base;
	public long start;
	public String start_time;
	public long end;
	public String end_time;
	public FFmpeg_ChapterTag tags;

	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
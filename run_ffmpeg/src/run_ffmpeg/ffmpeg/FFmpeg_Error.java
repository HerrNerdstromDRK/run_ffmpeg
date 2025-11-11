package run_ffmpeg.ffmpeg;

import com.google.gson.Gson;

public class FFmpeg_Error
{
	public int code;
	public String string;

	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}

package run_ffmpeg.ffmpeg;

import java.util.Map;

import com.google.gson.Gson;

public class FFmpeg_Format
{
	public String filename;
	public int nb_streams;
	public int nb_programs;

	public String format_name;
	public String format_long_name;
	public double start_time;

	/** Duration in seconds */
	public double duration;

	/** File size in bytes */
	public long size;

	/** Bitrate */
	public long bit_rate;

	public int probe_score;

	public Map<String, String> tags;

	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}


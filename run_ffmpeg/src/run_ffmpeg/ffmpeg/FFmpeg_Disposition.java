package run_ffmpeg.ffmpeg;

import com.google.gson.Gson;

public class FFmpeg_Disposition
{
	// NOTE: These should all be booleans, but ffprobe currently only outputs 0 or 1
	public int _default;
	public int dub;
	public int original;
	public int comment;
	public int lyrics;
	public int karaoke;
	public int forced;
	public int hearing_impaired;
	public int visual_impaired;
	public int clean_effects;
	public int attached_pic;
	public int captions;
	public int descriptions;
	public int metadata;

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}

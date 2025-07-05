package run_ffmpeg.ffmpeg;

import com.google.gson.Gson;

public class FFmpeg_ProbeFrame
{
	public FFmpeg_ProbeFrame()
	{}

	public String media_type = null ;
	public Integer stream_index = null ;
	public Integer key_frame = 0 ;
	public Integer pts = null ;
	public String pts_time = null ; 
	public Integer pkt_dts = null ;
	public String pkt_dts_time = null ;
	public Integer best_effort_timestamp = null ;
	public String best_effort_timestamp_time = null ;
	public Integer duration = null ;
	public String duration_time = null ;
	public String pkt_pos = null ;
	public String pkt_size = null ;
	public Integer width = null ;
	public Integer height = null ;
	public Integer crop_top = null ;
	public Integer crop_bottom = null ;
	public Integer crop_left = null ;
	public Integer crop_right = null ;
	public String pix_fmt = null ;
	public String sample_aspect_ratio = null ;
	public String pict_type = null ;
	public Integer interlaced_frame = null ;
	public Integer top_field_first = null ;
	public Integer repeat_pict = null ;
	public String chroma_location = null ;
	
	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}

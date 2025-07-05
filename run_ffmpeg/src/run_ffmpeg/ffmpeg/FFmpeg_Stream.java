package run_ffmpeg.ffmpeg;

import java.util.Map;

import com.google.gson.Gson;

/**
 * Data representing an ffmpeg/ffprobe stream.
 * @author Dan
 */
public class FFmpeg_Stream
{
	public enum CodecType {
		VIDEO,
		AUDIO,
		SUBTITLE,
		DATA,
		ATTACHMENT,
		ERROR
	}

	/**
	 * These are taken by name from the ffprobe result via json serialization.
	 */
	public int index;
	public String codec_name;
	public String codec_long_name;
	public String profile;
	public String codec_type;
	//	  public CodecType codec_type;
	public String codec_time_base;

	public String codec_tag_string;
	public String codec_tag;

	public int width, height;

	public int has_b_frames;

	public String sample_aspect_ratio;
	public String display_aspect_ratio;

	public String pix_fmt;
	public int level;
	public String chroma_location;
	public int refs;
	public String is_avc;
	public String nal_length_size;
	public String r_frame_rate;
	public String avg_frame_rate;
	public String time_base;

	public long start_pts;
	public double start_time;

	public long duration_ts;
	public double duration;

	public long bit_rate;
	public long max_bit_rate;
	public int bits_per_raw_sample;
	public int bits_per_sample;

	public long nb_frames;

	public String sample_fmt;
	public int sample_rate;
	public int channels;
	public String channel_layout;

	public FFmpeg_Disposition disposition;

	public Map<String, String> tags;

	public String getTagByName( final String tagName )
	{
		String tagValue = tags.get( tagName ) ;
		return tagValue ;
	}

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}

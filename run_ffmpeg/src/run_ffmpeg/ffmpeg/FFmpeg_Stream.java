package run_ffmpeg.ffmpeg;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Data representing an ffmpeg/ffprobe stream.
 * @author Dan
 */
public class FFmpeg_Stream
{
//	public enum CodecType {
//		VIDEO,
//		AUDIO,
//		SUBTITLE,
//		DATA,
//		ATTACHMENT,
//		ERROR
//	}

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

	@Override
	public String toString()
	{
		GsonBuilder builder = new GsonBuilder() ; 
		builder.setPrettyPrinting() ; 
		Gson gson = builder.create() ;
		final String json = gson.toJson( this ) ;
		return json ;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public String getCodec_name() {
		return codec_name;
	}

	public void setCodec_name(String codec_name) {
		this.codec_name = codec_name;
	}

	public String getCodec_long_name() {
		return codec_long_name;
	}

	public void setCodec_long_name(String codec_long_name) {
		this.codec_long_name = codec_long_name;
	}

	public String getProfile() {
		return profile;
	}

	public void setProfile(String profile) {
		this.profile = profile;
	}

	public String getCodec_type() {
		return codec_type;
	}

	public void setCodec_type(String codec_type) {
		this.codec_type = codec_type;
	}

	public String getCodec_time_base() {
		return codec_time_base;
	}

	public void setCodec_time_base(String codec_time_base) {
		this.codec_time_base = codec_time_base;
	}

	public String getCodec_tag_string() {
		return codec_tag_string;
	}

	public void setCodec_tag_string(String codec_tag_string) {
		this.codec_tag_string = codec_tag_string;
	}

	public String getCodec_tag() {
		return codec_tag;
	}

	public void setCodec_tag(String codec_tag) {
		this.codec_tag = codec_tag;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public int getHas_b_frames() {
		return has_b_frames;
	}

	public void setHas_b_frames(int has_b_frames) {
		this.has_b_frames = has_b_frames;
	}

	public String getSample_aspect_ratio() {
		return sample_aspect_ratio;
	}

	public void setSample_aspect_ratio(String sample_aspect_ratio) {
		this.sample_aspect_ratio = sample_aspect_ratio;
	}

	public String getDisplay_aspect_ratio() {
		return display_aspect_ratio;
	}

	public void setDisplay_aspect_ratio(String display_aspect_ratio) {
		this.display_aspect_ratio = display_aspect_ratio;
	}

	public String getPix_fmt() {
		return pix_fmt;
	}

	public void setPix_fmt(String pix_fmt) {
		this.pix_fmt = pix_fmt;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public String getChroma_location() {
		return chroma_location;
	}

	public void setChroma_location(String chroma_location) {
		this.chroma_location = chroma_location;
	}

	public int getRefs() {
		return refs;
	}

	public void setRefs(int refs) {
		this.refs = refs;
	}

	public String getIs_avc() {
		return is_avc;
	}

	public void setIs_avc(String is_avc) {
		this.is_avc = is_avc;
	}

	public String getNal_length_size() {
		return nal_length_size;
	}

	public void setNal_length_size(String nal_length_size) {
		this.nal_length_size = nal_length_size;
	}

	public String getR_frame_rate() {
		return r_frame_rate;
	}

	public void setR_frame_rate(String r_frame_rate) {
		this.r_frame_rate = r_frame_rate;
	}

	public String getAvg_frame_rate() {
		return avg_frame_rate;
	}

	public void setAvg_frame_rate(String avg_frame_rate) {
		this.avg_frame_rate = avg_frame_rate;
	}

	public String getTime_base() {
		return time_base;
	}

	public void setTime_base(String time_base) {
		this.time_base = time_base;
	}

	public long getStart_pts() {
		return start_pts;
	}

	public void setStart_pts(long start_pts) {
		this.start_pts = start_pts;
	}

	public double getStart_time() {
		return start_time;
	}

	public void setStart_time(double start_time) {
		this.start_time = start_time;
	}

	public long getDuration_ts() {
		return duration_ts;
	}

	public void setDuration_ts(long duration_ts) {
		this.duration_ts = duration_ts;
	}

	public double getDuration() {
		return duration;
	}

	public void setDuration(double duration) {
		this.duration = duration;
	}

	public long getBit_rate() {
		return bit_rate;
	}

	public void setBit_rate(long bit_rate) {
		this.bit_rate = bit_rate;
	}

	public long getMax_bit_rate() {
		return max_bit_rate;
	}

	public void setMax_bit_rate(long max_bit_rate) {
		this.max_bit_rate = max_bit_rate;
	}

	public int getBits_per_raw_sample() {
		return bits_per_raw_sample;
	}

	public void setBits_per_raw_sample(int bits_per_raw_sample) {
		this.bits_per_raw_sample = bits_per_raw_sample;
	}

	public int getBits_per_sample() {
		return bits_per_sample;
	}

	public void setBits_per_sample(int bits_per_sample) {
		this.bits_per_sample = bits_per_sample;
	}

	public long getNb_frames() {
		return nb_frames;
	}

	public void setNb_frames(long nb_frames) {
		this.nb_frames = nb_frames;
	}

	public String getSample_fmt() {
		return sample_fmt;
	}

	public void setSample_fmt(String sample_fmt) {
		this.sample_fmt = sample_fmt;
	}

	public int getSample_rate() {
		return sample_rate;
	}

	public void setSample_rate(int sample_rate) {
		this.sample_rate = sample_rate;
	}

	public int getChannels() {
		return channels;
	}

	public void setChannels(int channels) {
		this.channels = channels;
	}

	public String getChannel_layout() {
		return channel_layout;
	}

	public void setChannel_layout(String channel_layout) {
		this.channel_layout = channel_layout;
	}

	public FFmpeg_Disposition getDisposition() {
		return disposition;
	}

	public void setDisposition(FFmpeg_Disposition disposition) {
		this.disposition = disposition;
	}

	public Map<String, String> getTags() {
		return tags;
	}

	public void setTags(Map<String, String> tags) {
		this.tags = tags;
	}
	
//	public String toString()
//	{
//		Gson loginRequestGson = new Gson() ;
//		final String loginRequestJson = loginRequestGson.toJson( this ) ;
//		return loginRequestJson.toString() ;
//	}
}

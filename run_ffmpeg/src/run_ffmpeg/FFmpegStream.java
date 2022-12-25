package run_ffmpeg;

import java.util.Map;

public class FFmpegStream {

	  public enum CodecType {
	    VIDEO,
	    AUDIO,
	    SUBTITLE,
	    DATA,
	    ATTACHMENT,
	    ERROR
	  }
/*
	  public class CodecTypeDeserializer implements JsonDeserializer<CodecType> {
		  @Override
		  public CodecType deserialize( JsonElement jsonElement, Type typeOfT, JsonDeserializationContext context)
				  throws JsonParseException {
			  CodecType deserializedCodeType = CodecType.ERROR ;
			  if( jsonElement.getAsString().equals( "video" ) )
			  {
				  deserializedCodeType = CodecType.VIDEO ;
			  }
			  else if( jsonElement.getAsString().equals( "audio" ) )
			  {
				  deserializedCodeType = CodecType.AUDIO ;
			  }
			  else if( jsonElement.getAsString().equals( "subtitle" ) )
			  {
				  deserializedCodeType = CodecType.SUBTITLE ;
			  }
			  else if( jsonElement.getAsString().equals( "data" ) )
			  {
				  deserializedCodeType = CodecType.DATA ;
			  }
			  else if( jsonElement.getAsString().equals( "attachment" ) )
			  {
				  deserializedCodeType = CodecType.ATTACHMENT ;
			  }
			  return deserializedCodeType ;
		  }
	  }
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

	  public String sample_aspect_ratio; // TODO Change to a Ratio/Fraction object
	  public String display_aspect_ratio;

	  public String pix_fmt;
	  public int level;
	  public String chroma_location;
	  public int refs;
	  public String is_avc;
	  public String nal_length_size;
//	  public Fraction r_frame_rate;
//	  public Fraction avg_frame_rate;
//	  public Fraction time_base;
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

	  public FFmpegDisposition disposition;

	  public Map<String, String> tags;
	  
	  public String toString()
	  {
		  String retMe = "{"
				  + index
				  + "," + codec_name
				  + "," + codec_type ;
		  if( tags.containsKey( "title" ) )
		  {
			  retMe += "," + tags.get( "title" ) ;
		  }
		  if( tags.containsKey( "language" ) )
		  {
			  retMe += "," + tags.get( "language" ) ;
		  }
		  retMe += "}" ;
		  return retMe ;
	  }
	}

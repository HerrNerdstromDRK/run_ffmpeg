package run_ffmpeg.Plex;

import java.util.List;

import com.google.gson.Gson;

public class plexAPI_Media
{
	public Long id = null ;
	public Long duration = null ;
	public Long bitrate = null ;
	public Integer width = null ;
	public Integer height = null ;
	public Double aspectRatio = null ;
	public Integer audioChannels = null ;
	public Integer displayOffset = null ;
	public String audioCodec = null ;
	public String videoCodec = null ;
	public String videoResolution = null ;
	public String container = null ;
	public String videoFrameRate = null ;
	public String videoProfile = null ;
	public Boolean hasVoiceActivity = null ;
	public String audioProfile = null ;
	public Integer optimizedForStreaming = null ;
	public Boolean has64bitOffsets = null ;
	public List< plexAPI_Part > Part = null ;

	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}

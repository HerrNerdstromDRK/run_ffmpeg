package run_ffmpeg.Plex;

import java.util.List;

import com.google.gson.Gson;

public class Plex_Part
{
	public Boolean accessible = null ;
	public Boolean exists = null ;
	public Long id = null ;
	public String key = null ;
	public String indexes = null ;
	public Long duration = null ;
	public String file = null ;
	public Long size = null ;
	public Integer packetLength = null ;
	public String container = null ;
	public String videoProfile = null ;
	public String audioProfile = null ;
	public Boolean has64bitOffsets = null ;
	public Boolean optimizedForStreaming = null ;
	public String hasThumnail = null ;
	public List< Plex_Stream > Stream = null ;

	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}


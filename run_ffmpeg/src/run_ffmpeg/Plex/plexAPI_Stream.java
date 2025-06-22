package run_ffmpeg.Plex;

import com.google.gson.Gson;

public class plexAPI_Stream
{
	public String id = null ;
	public Integer streamType = null ;
	public String codec = null ;
	public Integer index = null ;
	public Long bitrate = null ;
	public String language = null ;
	public String languageTag = null ;
	public Boolean headerCompression = null ;
	public Integer DOVIBLCompatID = null ;
	public Boolean DOVIBLPresent = null ;
	public Integer DOVILevel = null ;
	public Boolean DOVIPresent = null ;
	public Integer DOVIProfile = null ;
	public String DOVIVersion = null ;
	public Integer bitDepth = null ;
	public String chromaLocation = null ;
	public String chromaSubsampling = null ;
	public Integer codedHeight = null ;
	public Integer codedWidth = null ;
	public String colorPrimaries = null ;
	public String colorRange = null ;
	public String colorSpace = null ;
	public String colorTrc = null ;
	public Double frameRate = null ;
	public Integer height = null ;
	public Integer level = null ;
	public Boolean original = null ;
	public Boolean hasScalingMatrix = null ;
	public String profile = null ;
	public String scanType = null ;
	public Integer refFrames = 1 ;
	public Integer width = null ;
	public String displayTitle = null ;
	public String extendedDisplayTitle = null ;
	public Boolean selected = null ;
	public Boolean forced = null ;
	public Integer channels = null ;
	public String audioChannelLayout = null ;
	public Long samplingRate = null ;
	public Boolean canAutoSync = null ;
	public Boolean hearingImpaired = null ;
	public Boolean dub = null ;
	public String title = null ;

	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}

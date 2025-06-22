package run_ffmpeg.Plex;

import java.util.List;

import com.google.gson.Gson;

public class plexAPI_MediaContainer
{
	public Integer size = null ;
	public Integer totalSize = null ;
	public Integer offset = null ;
	public Boolean allowsync = null ;
	public String art = null ;
	public String content = null ;
	public String identifier = null ;
	public Integer librarySectionID = null ;
	public String librarySectionTitle = null ;
	public String librarySectionUUID = null ;
	public String mediaTagPrefix = null ;
	public Long mediaTagVersion = null ;
	public String thumb = null ;
	public Boolean nocache = null ;
	public String title1 = null ;
	public String title2 = null ;
	public String viewGroup = null ;
	public plexAPI_Meta Meta = null ;
	public List< plexAPI_Metadata > Metadata = null ;
		
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}

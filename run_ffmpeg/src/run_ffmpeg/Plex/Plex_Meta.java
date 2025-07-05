package run_ffmpeg.Plex;

import java.util.List;

import com.google.gson.Gson;

public class Plex_Meta
{
	public List< Plex_Type > Type = null ;
	public List< Plex_FieldType > FieldType = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}

package run_ffmpeg.TheTVDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

public class TheTVDB_searchResponse
{
	public List< dataClass > data = new ArrayList< dataClass >() ;
	public String status = "" ;
	public TheTVDB_linksClass links = new TheTVDB_linksClass() ;

	public class dataClass
	{
		public List< String > aliases = new ArrayList< String >() ;
		public List< String > companies = new ArrayList< String >() ;
		public String country = "" ;
		public String companyType = "" ; 
		public String description = "" ;
		public String director = "" ;
		public String first_air_time = "" ;
		public List< String > genres = new ArrayList< String >() ;
		public String id = "" ;
		public String image_url = "" ;
		public Boolean is_official = false ;
		public String name = "" ;
		public String name_translated = "" ;
		public String network = "" ;
		public String objectID = "" ;
		public String officialList = "" ;
		public String overview = "" ;
		public Map< String, String > overviews = new HashMap< String, String >() ;
		public List< String > overview_translated = new ArrayList< String >() ;
		public String poster = "" ;
		public List< String > posters = new ArrayList< String >() ;
		public String primary_language = "" ;
		public List< Map< String, String > > remote_ids = new ArrayList< Map< String, String > >() ;
		public String status = "" ;
		public String slug = "" ;
		public List< String > studios = new ArrayList< String >() ;
		public String title = "" ;
		public String thumbnail = "" ;
		public Map< String, String > translations = new HashMap< String, String >() ;
		public List< String > translationsWithLang = new ArrayList< String >() ;
		public String tvdb_id = "" ;
		public String type = "" ;
		public String year = "" ;
	}

	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}

package run_ffmpeg.OpenSubtitles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OpenSubtitles_SearchRequest
{
	/// exclude, include (default: include)
	public String ai_translated = null ;
	
	/// For Tvshows
	public Integer episode_number = null ;
	
	/// exclude, include, only (default: include)
	public String foreign_parts_only = null ;
	
	/// include, exclude, only. (default: include)
	public String hearing_impaired = null ;
	
	/// ID of the movie or episode
	public Integer Id = null ;
	
	/// IMDB ID of the movie or episode
	public Integer imdb_id = null ;
	
	/// Language code(s), comma separated, sorted in alphabetical order (en,fr)
	public String languages = null ;
	
	/// exclude, include (default: exclude)
	public String machine_translated = null ;
	
	/// Moviehash of the moviefile
	/// >= 16 characters<= 16 characters
	/// Match pattern: ^[a-f0-9]{16}$
	public String moviehash = null ;
	
	/// include, only (default: include)
	public String moviehash_match = null ;
	
	/// Order of the returned results, accept any of above fields
	public String order_by = null ;
	
	/// Order direction of the returned results (asc,desc)
	public String order_direction = null ;
	
	/// Results page to display
	public Integer page = null ;
	
	/// For Tvshows
	public Integer parent_feature_id = null ;
	
	/// For Tvshows
	public Integer parent_imdb_id = null ;
	
	/// For Tvshows
	public Integer parent_tmdb_id = null ;
	
	/// file name or text search
	public String query = null ;
	
	/// For Tvshows
	public Integer season_number = null ;
	
	/// TMDB ID of the movie or episode
	public Integer tmdb_id = null ;
	
	/// include, only (default: include)
	public String trusted_sources = null ;
	
	/// movie, episode or all, (default: all)
	public String type = null ;
	
	/// To be used alone - for user uploads listing
	public Integer uploader_id = null ;
	
	/// Filter by movie/episode year
	public Integer year = null ;

	public String getAi_translated() {
		return ai_translated;
	}

	public void setAi_translated(String ai_translated) {
		this.ai_translated = ai_translated;
	}

	public Integer getEpisode_number() {
		return episode_number;
	}

	public void setEpisode_number(Integer episode_number) {
		this.episode_number = episode_number;
	}

	public String getForeign_parts_only() {
		return foreign_parts_only;
	}

	public void setForeign_parts_only(String foreign_parts_only) {
		this.foreign_parts_only = foreign_parts_only;
	}

	public String getHearing_impaired() {
		return hearing_impaired;
	}

	public void setHearing_impaired(String hearing_impaired) {
		this.hearing_impaired = hearing_impaired;
	}

	public Integer getId() {
		return Id;
	}

	public void setId(Integer id) {
		Id = id;
	}

	public Integer getImdb_id() {
		return imdb_id;
	}

	public void setImdb_id(Integer imdb_id) {
		this.imdb_id = imdb_id;
	}

	public String getLanguages() {
		return languages;
	}

	public void setLanguages(String languages) {
		this.languages = languages;
	}

	public String getMachine_translated() {
		return machine_translated;
	}

	public void setMachine_translated(String machine_translated) {
		this.machine_translated = machine_translated;
	}

	public String getMoviehash() {
		return moviehash;
	}

	public void setMoviehash(String moviehash) {
		this.moviehash = moviehash;
	}

	public String getMoviehash_match() {
		return moviehash_match;
	}

	public void setMoviehash_match(String moviehash_match) {
		this.moviehash_match = moviehash_match;
	}

	public String getOrder_by() {
		return order_by;
	}

	public void setOrder_by(String order_by) {
		this.order_by = order_by;
	}

	public String getOrder_direction() {
		return order_direction;
	}

	public void setOrder_direction(String order_direction) {
		this.order_direction = order_direction;
	}

	public Integer getPage() {
		return page;
	}

	public void setPage(Integer page) {
		this.page = page;
	}

	public Integer getParent_feature_id() {
		return parent_feature_id;
	}

	public void setParent_feature_id(Integer parent_feature_id) {
		this.parent_feature_id = parent_feature_id;
	}

	public Integer getParent_imdb_id() {
		return parent_imdb_id;
	}

	public void setParent_imdb_id(Integer parent_imdb_id) {
		this.parent_imdb_id = parent_imdb_id;
	}

	public Integer getParent_tmdb_id() {
		return parent_tmdb_id;
	}

	public void setParent_tmdb_id(Integer parent_tmdb_id) {
		this.parent_tmdb_id = parent_tmdb_id;
	}

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public Integer getSeason_number() {
		return season_number;
	}

	public void setSeason_number(Integer season_number) {
		this.season_number = season_number;
	}

	public Integer getTmdb_id() {
		return tmdb_id;
	}

	public void setTmdb_id(Integer tmdb_id) {
		this.tmdb_id = tmdb_id;
	}

	public String getTrusted_sources() {
		return trusted_sources;
	}

	public void setTrusted_sources(String trusted_sources) {
		this.trusted_sources = trusted_sources;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Integer getUploader_id() {
		return uploader_id;
	}

	public void setUploader_id(Integer uploader_id) {
		this.uploader_id = uploader_id;
	}

	public Integer getYear() {
		return year;
	}

	public void setYear(Integer year) {
		this.year = year;
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
}

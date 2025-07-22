package run_ffmpeg.OpenSubtitles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OpenSubtitles_FeatureDetails {

	private Integer feature_id;
	private String feature_type;
	private Integer year;
	private String title;
	private String movie_name;
	private Integer imdb_id;
	private Integer tmdb_id;
	private Integer season_number;
	private Integer episode_number;
	private Integer parent_imdb_id;
	private String parent_title;
	private Integer parent_tmdb_id;
	private Integer parent_feature_id;

	public Integer getYear() {
		return year;
	}

	public void setYear(Integer year) {
		this.year = year;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Integer getFeature_id() {
		return feature_id;
	}

	public void setFeature_id(Integer feature_id) {
		this.feature_id = feature_id;
	}

	public String getFeature_type() {
		return feature_type;
	}

	public void setFeature_type(String feature_type) {
		this.feature_type = feature_type;
	}

	public String getMovie_name() {
		return movie_name;
	}

	public void setMovie_name(String movie_name) {
		this.movie_name = movie_name;
	}

	public Integer getImdb_id() {
		return imdb_id;
	}

	public void setImdb_id(Integer imdb_id) {
		this.imdb_id = imdb_id;
	}

	public Integer getTmdb_id() {
		return tmdb_id;
	}

	public void setTmdb_id(Integer tmdb_id) {
		this.tmdb_id = tmdb_id;
	}

	public Integer getSeason_number() {
		return season_number;
	}

	public void setSeason_number(Integer season_number) {
		this.season_number = season_number;
	}

	public Integer getEpisode_number() {
		return episode_number;
	}

	public void setEpisode_number(Integer episode_number) {
		this.episode_number = episode_number;
	}

	public Integer getParent_imdb_id() {
		return parent_imdb_id;
	}

	public void setParent_imdb_id(Integer parent_imdb_id) {
		this.parent_imdb_id = parent_imdb_id;
	}

	public String getParent_title() {
		return parent_title;
	}

	public void setParent_title(String parent_title) {
		this.parent_title = parent_title;
	}

	public Integer getParent_tmdb_id() {
		return parent_tmdb_id;
	}

	public void setParent_tmdb_id(Integer parent_tmdb_id) {
		this.parent_tmdb_id = parent_tmdb_id;
	}

	public Integer getParent_feature_id() {
		return parent_feature_id;
	}

	public void setParent_feature_id(Integer parent_feature_id) {
		this.parent_feature_id = parent_feature_id;
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
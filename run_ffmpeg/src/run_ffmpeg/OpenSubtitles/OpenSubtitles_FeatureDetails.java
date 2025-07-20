package run_ffmpeg.OpenSubtitles;

import java.util.LinkedHashMap;
import java.util.Map;

public class OpenSubtitles_FeatureDetails {

	private Integer featureId;
	private String featureType;
	private Integer year;
	private String title;
	private String movieName;
	private Integer imdbId;
	private Integer tmdbId;
	private Integer seasonNumber;
	private Integer episodeNumber;
	private Integer parentImdbId;
	private String parentTitle;
	private Integer parentTmdbId;
	private Integer parentFeatureId;
	private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

	public Integer getFeatureId() {
		return featureId;
	}

	public void setFeatureId(Integer featureId) {
		this.featureId = featureId;
	}

	public String getFeatureType() {
		return featureType;
	}

	public void setFeatureType(String featureType) {
		this.featureType = featureType;
	}

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

	public String getMovieName() {
		return movieName;
	}

	public void setMovieName(String movieName) {
		this.movieName = movieName;
	}

	public Integer getImdbId() {
		return imdbId;
	}

	public void setImdbId(Integer imdbId) {
		this.imdbId = imdbId;
	}

	public Integer getTmdbId() {
		return tmdbId;
	}

	public void setTmdbId(Integer tmdbId) {
		this.tmdbId = tmdbId;
	}

	public Integer getSeasonNumber() {
		return seasonNumber;
	}

	public void setSeasonNumber(Integer seasonNumber) {
		this.seasonNumber = seasonNumber;
	}

	public Integer getEpisodeNumber() {
		return episodeNumber;
	}

	public void setEpisodeNumber(Integer episodeNumber) {
		this.episodeNumber = episodeNumber;
	}

	public Integer getParentImdbId() {
		return parentImdbId;
	}

	public void setParentImdbId(Integer parentImdbId) {
		this.parentImdbId = parentImdbId;
	}

	public String getParentTitle() {
		return parentTitle;
	}

	public void setParentTitle(String parentTitle) {
		this.parentTitle = parentTitle;
	}

	public Integer getParentTmdbId() {
		return parentTmdbId;
	}

	public void setParentTmdbId(Integer parentTmdbId) {
		this.parentTmdbId = parentTmdbId;
	}

	public Integer getParentFeatureId() {
		return parentFeatureId;
	}

	public void setParentFeatureId(Integer parentFeatureId) {
		this.parentFeatureId = parentFeatureId;
	}

	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(OpenSubtitles_FeatureDetails.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
		sb.append("featureId");
		sb.append('=');
		sb.append(((this.featureId == null)?"<null>":this.featureId));
		sb.append(',');
		sb.append("featureType");
		sb.append('=');
		sb.append(((this.featureType == null)?"<null>":this.featureType));
		sb.append(',');
		sb.append("year");
		sb.append('=');
		sb.append(((this.year == null)?"<null>":this.year));
		sb.append(',');
		sb.append("title");
		sb.append('=');
		sb.append(((this.title == null)?"<null>":this.title));
		sb.append(',');
		sb.append("movieName");
		sb.append('=');
		sb.append(((this.movieName == null)?"<null>":this.movieName));
		sb.append(',');
		sb.append("imdbId");
		sb.append('=');
		sb.append(((this.imdbId == null)?"<null>":this.imdbId));
		sb.append(',');
		sb.append("tmdbId");
		sb.append('=');
		sb.append(((this.tmdbId == null)?"<null>":this.tmdbId));
		sb.append(',');
		sb.append("seasonNumber");
		sb.append('=');
		sb.append(((this.seasonNumber == null)?"<null>":this.seasonNumber));
		sb.append(',');
		sb.append("episodeNumber");
		sb.append('=');
		sb.append(((this.episodeNumber == null)?"<null>":this.episodeNumber));
		sb.append(',');
		sb.append("parentImdbId");
		sb.append('=');
		sb.append(((this.parentImdbId == null)?"<null>":this.parentImdbId));
		sb.append(',');
		sb.append("parentTitle");
		sb.append('=');
		sb.append(((this.parentTitle == null)?"<null>":this.parentTitle));
		sb.append(',');
		sb.append("parentTmdbId");
		sb.append('=');
		sb.append(((this.parentTmdbId == null)?"<null>":this.parentTmdbId));
		sb.append(',');
		sb.append("parentFeatureId");
		sb.append('=');
		sb.append(((this.parentFeatureId == null)?"<null>":this.parentFeatureId));
		sb.append(',');
		sb.append("additionalProperties");
		sb.append('=');
		sb.append(((this.additionalProperties == null)?"<null>":this.additionalProperties));
		sb.append(',');
		if (sb.charAt((sb.length()- 1)) == ',') {
			sb.setCharAt((sb.length()- 1), ']');
		} else {
			sb.append(']');
		}
		return sb.toString();
	}

}
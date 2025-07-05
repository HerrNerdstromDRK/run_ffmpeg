package run_ffmpeg.Plex;

import java.util.List;

import com.google.gson.Gson;

public class Plex_Metadata
{
	public String ratingKey = null ;
	public String key = null ;
	public String guid = null ;
	public String slug = null ;
	public String studio = null ;
	public String type = null ;
	public String title = null ;
	public String titleSort = null ;
	public String contentRating = null ;
	public String summary = null ;
	public Double rating = null ;
	public Double audienceRating = null ;
	public Integer year = null ;
	public String tagline = null ;
	public String thumb = null ;
	public String art = null ;
	public String theme = null ;
	public Integer index = null ;
	public Integer leafCount = null ;
	public Integer viewedLeafCount = null ;
	public Integer seasonCount = null ;
	public Long duration = null ;
	public String originallyAvailableAt = null ;
	public Long addedAt = null ;
	public Long updatedAt = null ;
	public String audienceRatingImage = null ;
	public String chapterSource = null ;
	public String primaryExtraKey = null ;
	public String originalTitle = null ;
	public String parentRatingKey = null ;
	public String grandparentRatingKey = null ;
	public String parentGuid = null ;
	public String grandparentGuid = null ;
	public String grandparentSlug = null ;
	public String grandparentKey = null ;
	public String parentKey = null ;
	public String grandparentTitle = null ;
	public String grandparentThumb = null ;
	public String grandparentTheme = null ;
	public String grandparentArt = null ;
	public String parentTitle = null ;
	public Integer parentIndex = null ;
	public String parentThumb = null ;
	public String ratingImage = null ;
	public Integer viewCount = null ;
	public Long viewOffset = null ;
	public Integer skipCount = null ;
	public String subtype = null ;
	public Long lastRatedAt = null ;
	public String createdAtAccuracy = null ;
	public String createdAtTZOffset = null ;
	public Long lastViewedAt = null ;
	public Integer userRating = null ;
	public List< Plex_Image > Image = null ;
	public Plex_UltraBlurColors UltraBlurColors = null ;
	public List< Plex_Media > Media = null ;
	public List< Plex_Genre > Genre = null ;
	public List< Plex_Country > Country = null ;
	public List< Plex_Director > Director = null ;
	public List< Plex_Writer > Writer = null ;
	public List< Plex_Role > Role = null ;
	public List< Plex_Guid > Guid = null ;
	public List< Plex_Collection > Collection = null ;	

	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}

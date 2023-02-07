package run_ffmpeg;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates information about a movie or tv show. This includes all supporting files, such as extras.
 * @author Dan
 *
 */
public class MovieAndShowInfo
{
	/// The name of the movie.
	/// Will likely include the year, as in Plex format.
	/// Example: Godzilla King of the Monsters (2019)
	private String name = null ;
	
	/// If this is a TV show, then seasonName will contain the name of the season ("Season 04")
	/// If a movie, then seasonName will be null.
	private String seasonName = null ;
	
	/// The _id indices, kept in the database, of the source (.mkv) files for
	/// this movie and its subordinate extras.
	private List< String > mkvFiles = new ArrayList< String >() ; 

	/// Same, but for the mp4 files
	private List< String > mp4Files = new ArrayList< String >() ; 
	
	/// Constructor for a movie
	public MovieAndShowInfo( final String name )
	{
		this.name = name ;
	}
	
	/// Constructor for a tv show.
	public MovieAndShowInfo( final String name, final String seasonName )
	{
		this.name = name ;
		this.seasonName = seasonName ;
	}
	
	/**
	 * Add an MKV file to the list of mkv files. This will include the full path to the file.
	 * @param mkvFileName
	 */
	public void addMKVFile( final String mkvFileName )
	{
		mkvFiles.add( mkvFileName ) ;
	}

	/**
	 * Add an MP4 file to the list of mp4 files. This will include the full path to the file.
	 * @param mp4FileName
	 */
	public void addMP4File( final String mp4FileName )
	{
		mp4Files.add( mp4FileName ) ;
	}
	
	public String getName()
	{
		return name ;
	}
	
	public String getSeasonName()
	{
		return seasonName ;
	}
	
	public void setSeasonName( String seasonName )
	{
		this.seasonName = seasonName ;
	}
	
	public String toString()
	{
		String retMe = "{name: " + getName() ;
		if( getSeasonName() != null )
		{
			retMe += ", season: " + getSeasonName() ;
		}
		retMe += ",[" ;
		for( String mkvFile : mkvFiles )
		{
			retMe += mkvFile + " " ;
		}
		retMe += "],[" ;
		for( String mp4File : mp4Files )
		{
			retMe += mp4File + " " ;
		}
		retMe += "]}" ;
		return retMe ;
	}
	
}

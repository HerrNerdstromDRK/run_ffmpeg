package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;

import com.google.gson.Gson;

/**
 * Used to encapsulate the choices for a movie -- does the requested movie refer to movie A, or movie B?
 */
public class JobRecord_MakeMovieChoice
{
	public String inputDirectoryAbsolutePath = "" ;
	public String baseName = "" ;
	/// Pair is < posterUrl, tmdbInfo >
	public ArrayList< ConcretePair_StringLong > posterUrls = new ArrayList< ConcretePair_StringLong >() ;
	
	public JobRecord_MakeMovieChoice()
	{}
	
	public JobRecord_MakeMovieChoice( final File inputDirectory, final String baseName )
	{
		inputDirectoryAbsolutePath = inputDirectory.getAbsolutePath() ;
		this.baseName = baseName ;
	}
	
	public void addPair( ConcretePair_StringLong pair )
	{
		posterUrls.add( pair ) ;
	}
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}

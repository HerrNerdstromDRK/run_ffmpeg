package run_ffmpeg;

import com.google.gson.Gson;

public class ConcretePair_StringLong
{
	protected String left = null ;
	protected Long right = null ;
	
	public ConcretePair_StringLong( String left, Long right )
	{
		this.left = left ;
		this.right = right ;
	}

	public String getLeft()
	{
		return left ;
	}

	public Long getRight()
	{
		return right ;
	}
	
	public void setLeft( String left )
	{
		this.left = left ;
	}
	
	public void setRight( Long right )
	{
		this.right = right ;
	}
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}

package cecs429.query;
import cecs429.text.TokenProcessor;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses boolean queries according to the base requirements of the CECS 429 project.
 * Does not handle phrase queries, NOT queries, NEAR queries, or wildcard queries... yet.
 */
public class BooleanQueryParser {
	TokenProcessor tokenProcessor;

	public BooleanQueryParser(TokenProcessor tokenProcessor){
		this.tokenProcessor = tokenProcessor;
	}

	/**
	 * Identifies a portion of a string with a starting index and a length.
	 */
	private static class StringBounds {
		int start;
		int length;

		StringBounds(int start, int length) {
			this.start = start;
			this.length = length;
		}
	}

	/**
	 * Encapsulates a Query and the StringBounds that led to its parsing.
	 */
	private static class Literal {
		StringBounds bounds;
		Query literalComponent;

		Literal(StringBounds bounds, Query literalComponent) {
			this.bounds = bounds;
			this.literalComponent = literalComponent;
		}
	}

	/**
	 * Given a boolean query, parses and returns a tree of Query objects representing the query.
	 */
	public Query parseQuery(String query) {
		int start = 0;
		List<Query> allSubqueries = new ArrayList<>();
		do {
			// Identify the next subquery: a portion of the query up to the next + sign.
			StringBounds nextSubquery = findNextSubquery(query, start);
			// Extract the identified subquery into its own string.
			String subquery = query.substring(nextSubquery.start, nextSubquery.start + nextSubquery.length);
			int subStart = 0;

			// Store all the individual components of this subquery.
			List<Query> subqueryLiterals = new ArrayList<>(0);

			do {
				// Extract the next literal from the subquery.
				Literal lit = findNextLiteral(subquery, subStart);

				// Add the literal component to the conjunctive list.
				subqueryLiterals.add(lit.literalComponent);

				// Set the next index to start searching for a literal.
				subStart = lit.bounds.start + lit.bounds.length;

			} while (subStart < subquery.length());

			// After processing all literals, we are left with a conjunctive list
			// of query components, and must fold that list into the final disjunctive list
			// of components.

			// If there was only one literal in the subquery, we don't need to AND it with anything --
			// its component can go straight into the list.
			if (subqueryLiterals.size() == 1) {
				allSubqueries.add(subqueryLiterals.get(0));
			}
			else {
				// With more than one literal, we must wrap them in an AndQuery component.
				allSubqueries.add(new AndQuery(subqueryLiterals));
			}
			start = nextSubquery.start + nextSubquery.length;
		} while (start < query.length());

		// After processing all subqueries, we either have a single component or multiple components
		// that must be combined with an OrQuery.
		if (allSubqueries.size() == 1) {
			return allSubqueries.get(0);
		}
		else if (allSubqueries.size() > 1) {
			return new OrQuery(allSubqueries);
		}
		else {
			return null;
		}
	}

	/**
	 * Locates the start index and length of the next subquery in the given query string,
	 * starting at the given index.
	 */
	private StringBounds findNextSubquery(String query, int startIndex) {
		int lengthOut;

		// Find the start of the next subquery by skipping spaces and + signs.
		char test = query.charAt(startIndex);
		while (test == ' ' || test == '+') {
			test = query.charAt(++startIndex);
		}

		// Find the end of the next subquery.
		int nextPlus = query.indexOf('+', startIndex + 1);

		if (nextPlus < 0) {
			// If there is no other + sign, then this is the final subquery in the
			// query string.
			lengthOut = query.length() - startIndex;
		}
		else {
			// If there is another + sign, then the length of this subquery goes up
			// to the next + sign.

			// Move nextPlus backwards until finding a non-space non-plus character.
			test = query.charAt(nextPlus);
			while (test == ' ' || test == '+') {
				test = query.charAt(--nextPlus);
			}

			lengthOut = 1 + nextPlus - startIndex;
		}

		// startIndex and lengthOut give the bounds of the subquery.
		return new StringBounds(startIndex, lengthOut);
	}

	/**
	 * Locates and returns the next literal from the given subquery string.
	 */
	private Literal findNextLiteral(String subquery, int startIndex) {
		int subLength = subquery.length();
		boolean isPhraseLiteral=false;
		boolean isNegativeLiteral=false;
		int lengthOut=0;

		// Skip past white space.
		while (subquery.charAt(startIndex) == ' ' ) {
			++startIndex;

		}

		// Locate the next space to find the end of this literal.
		int nextSpace = subquery.indexOf(' ', startIndex);
		// Locate the next doublequotes to find the end of this literal.
		int dquoteStartIndex = subquery.indexOf('"', startIndex);
		if (nextSpace < 0 && dquoteStartIndex<0) {
			// No more literals in this subquery. The subquery is the literal.

			lengthOut = subLength - startIndex;
		}
		else if (nextSpace >= 0 && dquoteStartIndex<0){
			lengthOut = nextSpace - startIndex;
		}
		else if (nextSpace < 0 && dquoteStartIndex >=0)
		{
			int dquoteEndIndex=subquery.indexOf('"', dquoteStartIndex+1);
			if(dquoteEndIndex<0)
			{
				// if the query is like -> what is binomial expansion of "1 + x" to the power n.
				//then our main subquery will be-> 'what is binomial expansion of "1 '
				//more specifically in this block the subquery will be '"1 '
				if(subquery.charAt(startIndex) == '"') {
					//discard the startDdoubleQuotes as we can't find its ending pair. Also, there is no space. So this is just a signle word/term.
					//Example:"1-> becomes 1
					startIndex++;
					lengthOut = subquery.length() - startIndex;
					isPhraseLiteral = false;
				}
				else
				{
					//if query like this-> xy"cc, then we send the subquery as xy..so that on next method call, we parse "cc
					lengthOut=dquoteStartIndex-startIndex;
					isPhraseLiteral=false;
				}

			}
			else {
				//Assuming the user never gives a query like-> xy"cccs" . And always gives the same query like this-> xy "cccs"
				//hence here always dquoteStartIndex will be equal to startIndex i.e., dquoteStartIndex==startIndex
				if(subquery.charAt(startIndex) == '"') {
					//if query like this-> xy "cccs"
					lengthOut = dquoteEndIndex - startIndex;
					isPhraseLiteral = true;
				}
				else
				{
					//if query like this-> xy"cccs", then we send the subquery as xy..so that on next method call, we parse "cccs"
					lengthOut=dquoteStartIndex-startIndex;
					isPhraseLiteral=false;
				}

			}
		}
		else //nextSpace > 0 && dquoteStartIndex >0
		{
			if(dquoteStartIndex<nextSpace)
			{
				int dquoteEndIndex=subquery.indexOf('"', dquoteStartIndex+1);
				if(dquoteEndIndex<0)
				{
					// if the query is like -> what is binomial expansion of "(y z) + x" to the power n.
					//then our main subquery will be-> 'what is binomial expansion of '"(y z) '
					//more specifically in this block the subquery will be '"(y z) '
					if(subquery.charAt(startIndex) == '"') {
						//if query like this-> "(y z)
						startIndex++;
						lengthOut = subquery.indexOf(" ",startIndex) - startIndex;
						isPhraseLiteral = false;
					}
					else
					{
						//if query like this-> xy"(y z) + x",
						// More specifically xy"(y z)  then we send the subquery as xy..so that on next method call, we parse "(y z)
						lengthOut=dquoteStartIndex-startIndex;
						isPhraseLiteral=false;
					}
				}
				else {
					//Assuming the user never gives a query like-> xy"cc cs" . And always gives the same query like this-> xy "cc cs".
					// In this case the subquery will be like->"cc cs" , as dquoteStartIndex < nextSpace
					//hence here always dquoteStartIndex will be equal to startIndex i.e., dquoteStartIndex==startIndex
					if(subquery.charAt(startIndex) == '"') {
						//if query like this-> xy "cc cs"
						lengthOut = dquoteEndIndex - startIndex;
						isPhraseLiteral = true;
					}
					else
					{
						//if query like this-> xy"cc cs", then we send the subquery as xy..so that on next method call, we parse "cc cs"
						lengthOut=dquoteStartIndex-startIndex;
						isPhraseLiteral=false;
					}
				}
			}
			else
			{
				lengthOut = nextSpace - startIndex;
			}
		}
		// This is a term literal containing a single term.
		if(!isPhraseLiteral) {
			if(subquery.charAt(startIndex)=='-')
			{
				isNegativeLiteral=true;
				startIndex++;
				lengthOut--;
			}
			final String substring = subquery.substring(startIndex, startIndex + lengthOut);
			if (substring.contains("*")){
				return new Literal(
						new StringBounds(startIndex,lengthOut),
						new WildcardLiteral(substring,tokenProcessor,isNegativeLiteral)
				);
			}else{
				return new Literal(
						new StringBounds(startIndex, lengthOut),
						new TermLiteral(substring,tokenProcessor,isNegativeLiteral));
			}
		}
		// This is a phrase literal.
		else{
			return new Literal(
					new StringBounds(startIndex, lengthOut+1),
					new PhraseLiteral(subquery.substring(startIndex, startIndex + lengthOut + 1),tokenProcessor,isNegativeLiteral));
		}

	}
}

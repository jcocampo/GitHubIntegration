package com.nbcuni.githubconnect.integration;

import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map.Entry;

import jxl.CellView;
import jxl.Workbook;
import jxl.format.Colour;
import jxl.write.Label;
import jxl.write.WritableCell;
import jxl.write.WritableCellFormat;
import jxl.write.WritableFont;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

public class GetIssuesReport {
	
	private static String OathToken="";
	
	private static ArrayList<String> getRepos() throws Exception {
		ArrayList<String> gitRepos = new ArrayList<String>();
		gitRepos.add("NewsCMS");
		gitRepos.add("nbcnews.com");
		gitRepos.add("NBCNewsRendering");
		gitRepos.add("news-api");
		gitRepos.add("image-listener");
		
		return gitRepos;
	}
	
	public static void main(String[] args) throws Exception {
		setAuthToken(args[0]);
		String xlsFile = "DefectReport.xls";
		deleteXLSFile(xlsFile);
		
		for (String repos:getRepos()){
			
			System.out.println("***** Generating Report for GitHub Repo: "+ repos + " *****");
			getResults(repos, "open", true, xlsFile, "Outstanding Defects (bug)");
			getResults(repos, "closed", true, xlsFile, "Defect Kill List (bug)");
			getResults(repos, "open", false, xlsFile, "Outstanding Issues (non-bug)");
			getResults(repos, "closed", false, xlsFile, "Issues Kill List (non-bug)");
			System.out.println("***** Finished Generating Report for GitHub Repo: "+ repos + " *****\n\n\n");
		}
		
		System.out.println("Processing Completed.  Please see File ("+xlsFile+")");
	}
	
	private static void setAuthToken (String token) throws Exception {
		OathToken = token;
	}
	
	private static String getAuthToken () throws Exception {
		return OathToken;
	}
	private static void getResults(String repos, String state, boolean isBug, String xlsFile, String sheetName) throws Exception{
		int ctr=25;
		for (int i=1;i<ctr;i++){
			
			JsonElement rootObj = getGitHubResponse(repos, state, isBug, i);
			JsonObject mainRootObj = rootObj.getAsJsonObject();
			JsonElement items = mainRootObj.get("items");
			if (items != null){
				if (items.getAsJsonArray().size() >0 ){
					ArrayList<HashMap<String, String>> result = processResults(repos, items, isBug);
					generateExcelFile(result, xlsFile, sheetName, true);
				} else {
					break;
				}
			} else {
				break;
			}
			
			Thread.sleep (10000);
		}
		
		System.out.println("Defect Report Generated - "+ sheetName);
	}
	
	private static ArrayList<HashMap<String, String>> processResults(String ghRepo, JsonElement rootObj, boolean getBugsOnly) throws Exception{
		ArrayList<HashMap<String, String>> defectList = new ArrayList<HashMap<String,String>>();
		HashMap<String, String> tempMap = new HashMap<String, String>();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date startDate = sdf.parse(getLast7Days());
        Date endDate = sdf.parse(getCurrentDate());
        
		if (rootObj != null) {
            JsonArray rootObject = rootObj.getAsJsonArray();

            for (int i=0; i<rootObject.size(); i++){
            	JsonElement doc = rootObject.get(i);
            	JsonObject docItemRoot = doc.getAsJsonObject();
            	
            	//for Bugs only
            	if (getBugsOnly){
            		//for Closed issues only
            		if (docItemRoot.get("state").getAsString().equals("closed")){
            			
            			Date closedDate = sdf.parse(docItemRoot.get("closed_at").getAsString());

                		if (closedDate.after(startDate) && closedDate.before(endDate)){
                			tempMap.put("GH Repository", ghRepo);
                    		tempMap.put("DefectID", docItemRoot.get("number").getAsString());
                    		tempMap.put("Description", docItemRoot.get("title").getAsString());
                    		tempMap.put("Status", docItemRoot.get("state").getAsString().toUpperCase());
                    		tempMap.put("Submitted By", getSubmittedBy(docItemRoot));
                    		tempMap.put("Assigned To", getAssignedTo(docItemRoot));
                    		tempMap.put("Date Opened", docItemRoot.get("created_at").getAsString());
                			tempMap.put("Closed On", docItemRoot.get("closed_at").getAsString());
                			tempMap.put("Labels", getLabels(docItemRoot));
                    		defectList.add(new HashMap<String, String>(tempMap));
                    		tempMap.clear();
                		}
                	} else {
                		
                		//For Open issues Only
                		tempMap.put("GH Repository", ghRepo);
                		tempMap.put("DefectID", docItemRoot.get("number").getAsString());
                		tempMap.put("Description", docItemRoot.get("title").getAsString());
                		tempMap.put("Status", docItemRoot.get("state").getAsString().toUpperCase());
                		tempMap.put("Submitted By", getSubmittedBy(docItemRoot));
                		tempMap.put("Assigned To", getAssignedTo(docItemRoot));
                		tempMap.put("Date Opened", docItemRoot.get("created_at").getAsString());
                		tempMap.put("Last Updated", docItemRoot.get("updated_at").getAsString());
                		tempMap.put("Defect Age", calculateDefectAge(docItemRoot.get("created_at").getAsString()));
                		tempMap.put("Labels", getLabels(docItemRoot));
                		defectList.add(new HashMap<String, String>(tempMap));
                		tempMap.clear();
                	}
            	} else {
            		//For Non Bug issues
            		if (!isBug(docItemRoot)){
            			
            			//for Non-bug issues that are closed
            			if (docItemRoot.get("state").getAsString().equals("closed")){
                    		Date closedDate = sdf.parse(docItemRoot.get("closed_at").getAsString());

                    		if (closedDate.after(startDate) && closedDate.before(endDate)){
                    			tempMap.put("GH Repository", ghRepo);
                        		tempMap.put("DefectID", docItemRoot.get("number").getAsString());
                        		tempMap.put("Description", docItemRoot.get("title").getAsString());
                        		tempMap.put("Status", docItemRoot.get("state").getAsString().toUpperCase());
                        		tempMap.put("Submitted By", getSubmittedBy(docItemRoot));
                        		tempMap.put("Assigned To", getAssignedTo(docItemRoot));
                        		tempMap.put("Date Opened", docItemRoot.get("created_at").getAsString());
                    			tempMap.put("Closed On", docItemRoot.get("closed_at").getAsString());
                    			tempMap.put("Labels", getLabels(docItemRoot));
                        		defectList.add(new HashMap<String, String>(tempMap));
                        		tempMap.clear();
                    		}
                    	} else {
                    		
                    		//for Open Non-bug issues
                    		tempMap.put("GH Repository", ghRepo);
                    		tempMap.put("DefectID", docItemRoot.get("number").getAsString());
                    		tempMap.put("Description", docItemRoot.get("title").getAsString());
                    		tempMap.put("Status", docItemRoot.get("state").getAsString().toUpperCase());
                    		tempMap.put("Submitted By", getSubmittedBy(docItemRoot));
                    		tempMap.put("Assigned To", getAssignedTo(docItemRoot));
                    		tempMap.put("Date Opened", docItemRoot.get("created_at").getAsString());
                    		tempMap.put("Last Updated", docItemRoot.get("updated_at").getAsString());
                    		tempMap.put("Defect Age", calculateDefectAge(docItemRoot.get("created_at").getAsString()));
                    		tempMap.put("Labels", getLabels(docItemRoot));
                    		defectList.add(new HashMap<String, String>(tempMap));
                    		tempMap.clear();
                    	}
                	}
            	}
            }
		}
		
		return defectList;
	}
	
	
	private static String getCurrentDate() throws Exception{
		Locale loc = Locale.getDefault();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", loc);
        Date date = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.add(Calendar.DATE,0);
        date.setTime(c.getTime().getTime());
        return dateFormat.format(date);
	}
	
	private static String getLast7Days() throws Exception{
		Locale loc = Locale.getDefault();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", loc);
        Date date = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.add(Calendar.DATE, -8);
        date.setTime(c.getTime().getTime());
        return dateFormat.format(date);

	}
	
	private static String calculateDefectAge(String sDate) throws Exception {
		Locale loc = Locale.getDefault();
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", loc);
		Date d1 = null;
		Date d2 = null;
        Date date = new Date();
 
		try {
			d1 = dateFormat.parse(sDate);
			d2 = dateFormat.parse(dateFormat.format(date));
			long diff = d2.getTime() - d1.getTime();
			long diffDays = diff / (24 * 60 * 60 * 1000);
			
			if (diffDays > 1){
				return String.valueOf(diffDays) + " Days";
			} else {
				return String.valueOf(diffDays) + " Day";
			}
			
		
		} catch (Exception ex){
			System.out.println(ex);
		}
	    
	    return "";
	}
	
	
	@SuppressWarnings("unused")
	private static void generateReport(ArrayList<HashMap<String, String>> finalList) throws Exception{
		
		for (HashMap<String, String> xxx:finalList){
			
			for (Entry<String, String> yyy: xxx.entrySet()){
				System.out.println(yyy.getKey()+ ": " + yyy.getValue());
			}
			
			System.out.println ("\n\n\n");
		}
	}
	
	
	
	private static String getSubmittedBy(JsonObject docObject) throws Exception{
		if (docObject.has("user")){
    		 return docObject.getAsJsonObject("user").get("login").getAsString();
    	}
		
		return "";
	}
	
	private static String getAssignedTo(JsonObject docObject) throws Exception{
		if (docObject.has("assignee")){
			if (!docObject.get("assignee").isJsonNull()){
				return docObject.getAsJsonObject("assignee").get("login").getAsString();
			}
		}
		return "";
	}
	
	
	
	private static boolean isBug(JsonObject docObject) throws Exception {
		if (docObject.has("labels")){
    		JsonArray labels = docObject.getAsJsonArray("labels");
    		
    		if (labels.size() > 0 ){
    			for (int j=0; j<labels.size();j++){
    				JsonElement labelRecords = labels.get(j);
    				String type = labelRecords.getAsJsonObject().get("name").getAsString();
    				
    				if (type.equals("bug")){
    					return true;
    				}
    			}
    		}
    		
    	}
		
		return false;
	}
	
	private static String getLabels(JsonObject docObject) throws Exception {
		String labelList = "";
		
		if (docObject.has("labels")){
    		JsonArray labels = docObject.getAsJsonArray("labels");
    		
    		if (labels.size() > 0 ){
    			for (int j=0; j<labels.size();j++){
    				JsonElement labelRecords = labels.get(j);
    				String type = labelRecords.getAsJsonObject().get("name").getAsString();
    				if (j==0){
    					labelList = type;
    				} else {
    					labelList = labelList+ ", " + type;
    				}
    			}
    		}
    		
    	}
		
		return labelList;
	}
	
	private static JsonElement getGitHubResponse(String repoName, String state, boolean getBugsOnly, int pageNo) throws Exception{
//		String url = "https://api.github.com/repos/nbcnews/" +repoName + "/issues?page="+page+"&state=closed";
		String url = "";
		if (state.equals("open")){
			if (getBugsOnly){
				url = "https://api.github.com/search/issues?q=repo:nbcnews/"+repoName+"+state:"+state+"+label:bug&sort=created&order=asc";
			} else {
				url = "https://api.github.com/search/issues?q=repo:nbcnews/"+repoName+"+state:"+state+"&sort=created&order=asc";
			}
		} else {
			if (getBugsOnly){
				url = "https://api.github.com/search/issues?q=repo:nbcnews/"+repoName+"+state:"+state+"+label:bug+updated:"+getLast7Days()+".."+getCurrentDate()+"&sort=created&order=asc";
			} else {
				url = "https://api.github.com/search/issues?q=repo:nbcnews/"+repoName+"+state:"+state+"+updated:"+getLast7Days()+".."+getCurrentDate()+"&sort=created&order=asc";
			}
		}
		
		//append page number
		url = url +"&page="+pageNo;
		
		URLConnection urlConnection=null;
		urlConnection = new URL(url).openConnection();
		
		urlConnection.setRequestProperty("Authorization", "token " + getAuthToken());
		urlConnection.connect();

        JsonReader reader = getHttpResponse(urlConnection);
        reader.setLenient(true);
        
        JsonParser parser = new JsonParser();
        JsonElement rootElement=null;
        rootElement = parser.parse(reader);
        return rootElement;
		
	}
	
	private static JsonReader getHttpResponse(URLConnection con) throws Exception {
        // Cast to a HttpURLConnection
        JsonReader reader = null;
        if (con instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection) con;
            int code = httpConnection.getResponseCode();
            if (code != 200) {
                reader = new JsonReader(new InputStreamReader(httpConnection.getErrorStream()));
            } else {
                reader = new JsonReader(new InputStreamReader(httpConnection.getInputStream()));
            }
           
        } else {
            System.err.println("HTTP API call error - not a valid http request!");
        }
 
        return reader;
    }
	
	
	private static boolean fileExists(String file) throws Exception {
		File f = new File(file);
		return f.exists();
	}
	
	
	private static void generateExcelFile(ArrayList<HashMap<String, String>> inputList, String sFile, String sheetName, boolean append) throws Exception{
		
		if (!inputList.isEmpty()){
			if (!fileExists(sFile)){
				WritableWorkbook workbook = Workbook.createWorkbook(new File(sFile));
				WritableSheet sheet = workbook.createSheet(sheetName, 0);
				generateColumnHeaders(inputList, sheet);
				generateSheetContent(inputList, sheet);
				
				workbook.write();
			    workbook.close();
			} else {
				Workbook existingWorkbook = Workbook.getWorkbook(new File(sFile));
				WritableWorkbook workbookCopy = Workbook.createWorkbook(new File(sFile), existingWorkbook);
				WritableSheet sheetToEdit = workbookCopy.getSheet(sheetName);
				
				if (sheetToEdit == null){
					sheetToEdit = workbookCopy.createSheet(sheetName, 0);
					generateColumnHeaders(inputList, sheetToEdit);
				}
				
				generateSheetContent(inputList, sheetToEdit);
				
				workbookCopy.write();
				workbookCopy.close();
				existingWorkbook.close();
			}
		}
		
	}
	
	private static void generateColumnHeaders(ArrayList<HashMap<String, String>> inputList, WritableSheet sheetName) throws Exception {
		HashMap<String, String> tempx = inputList.get(0);
		
		WritableFont arial12ptBold = new WritableFont(WritableFont.ARIAL, 12, WritableFont.BOLD);
		WritableCellFormat arial12BoldFormat = new WritableCellFormat(arial12ptBold);
		arial12BoldFormat.setWrap(true);
		arial12BoldFormat.setBackground(Colour.GREY_40_PERCENT);
		
		CellView cf = new CellView();
		cf.setAutosize(true);
		  
		int col=0;
		int row =0;
		for (String title:tempx.keySet()){
			Label label = new Label(col, row, title, arial12BoldFormat);
			sheetName.addCell(label);
			sheetName.setColumnView(col, cf);
			col++;
		}
		
		
	}
	
	private static void generateSheetContent(ArrayList<HashMap<String, String>> inputList, WritableSheet sheetName) throws Exception {
		int col=0;
		int row =sheetName.getRows();
		
		
		for (HashMap<String, String> contentList:inputList){
			boolean markAsRed = false;
			boolean markAsOrange = false;
			boolean markAsYellow = false;
			if (contentList.get("Labels").contains("P1") || contentList.get("Labels").contains("Pri 1")){
				markAsOrange = true;
			}
			
			if (contentList.get("Labels").contains("P0")){
				markAsRed = true;
			}
			
			if (contentList.get("Labels").contains("P2") || contentList.get("Labels").contains("Pri 2")){
				markAsYellow = true;
			}
			
			for (Entry<String, String> content: contentList.entrySet()){
				
				WritableFont arial12ptBold = new WritableFont(WritableFont.ARIAL, 10);
				WritableCellFormat arial12BoldFormat = new WritableCellFormat(arial12ptBold);
				arial12BoldFormat.setWrap(true);
				Label label;
				
				if (markAsRed){
					arial12BoldFormat.setBackground(Colour.RED);
					label = new Label(col, row, content.getValue(), arial12BoldFormat);
				} else if (markAsOrange){
					arial12BoldFormat.setBackground(Colour.LIGHT_ORANGE);
					label = new Label(col, row, content.getValue(), arial12BoldFormat);
//				} else if (markAsYellow){
//					arial12BoldFormat.setBackground(Colour.YELLOW);
//					label = new Label(col, row, content.getValue(), arial12BoldFormat);
				} else {
					label = new Label(col, row, content.getValue());
				}
				
				WritableCell cell = (WritableCell) label;
				sheetName.addCell(cell);
				col++;	
			}
			col=0;
			row++;
		}
	}
	
	private static void deleteXLSFile(String sFile) throws Exception {
		File f = new File(sFile);
		if (f.exists()){
			f.delete();
		}
		
		
	}
}

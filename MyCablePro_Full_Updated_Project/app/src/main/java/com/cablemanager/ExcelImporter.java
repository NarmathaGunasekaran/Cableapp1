package com.cablemanager;

import android.content.Context;
import android.net.Uri;
import org.apache.poi.ss.usermodel.*;
import java.io.InputStream;
import java.util.*;

public class ExcelImporter {
    public static class Result {
        public final List<Customer> customers=new ArrayList<>();
        public int skipped=0, errors=0;
        public final List<String> errorMessages=new ArrayList<>();
    }

    public static Result read(Context context, Uri uri) {
        Result result=new Result();
        try (InputStream in=context.getContentResolver().openInputStream(uri);
             Workbook wb=WorkbookFactory.create(in)) {
            DataFormatter fmt=new DataFormatter();
            if(wb.getNumberOfSheets()==0) throw new IllegalArgumentException("Excel has no sheets");
            Sheet sheet=wb.getSheetAt(0);
            int headerRow=-1;
            Map<String,Integer> h=new HashMap<>();

            for(int r=0;r<=Math.min(sheet.getLastRowNum(),30);r++){
                Row row=sheet.getRow(r);
                if(row==null) continue;
                Map<String,Integer> temp=new HashMap<>();
                for(Cell cell:row){
                    String key=norm(fmt.formatCellValue(cell));
                    if(!key.isEmpty()) temp.put(key,cell.getColumnIndex());
                }
                if(temp.containsKey("boxid") && (temp.containsKey("customername") || temp.containsKey("name"))){
                    headerRow=r; h=temp; break;
                }
            }

            if(headerRow<0) throw new IllegalArgumentException(
                    "Headers not found. Required: BOX ID and CUSTOMER NAME");

            Row hr=sheet.getRow(headerRow);
            Map<String,Integer> headers=new HashMap<>();
            for(Cell cell:hr){
                String key=norm(fmt.formatCellValue(cell));
                if(!key.isEmpty()) headers.put(key,cell.getColumnIndex());
            }

            for(int r=headerRow+1;r<=sheet.getLastRowNum();r++){
                Row row=sheet.getRow(r);
                if(row==null){ result.skipped++; continue; }

                String box=get(row,headers,"boxid",fmt);
                String name=getAny(row,headers,fmt,"customername","name");

                if(box.isEmpty() && name.isEmpty()){ result.skipped++; continue; }
                if(box.isEmpty()){
                    result.errors++;
                    result.errorMessages.add("Row "+(r+1)+": BOX ID is empty");
                    continue;
                }

                Customer c=new Customer();
                c.boxId=box;
                c.cardNo=getAny(row,headers,fmt,"cardno","smartcardno","smartcard");
                c.mso=get(row,headers,"mso",fmt);
                c.name=name;
                c.phone=getAny(row,headers,fmt,"phoneno","phone","mobileno","mobile");
                c.address=get(row,headers,"address",fmt);
                c.zone=get(row,headers,"zone",fmt);
                c.searchCode=get(row,headers,"searchcode",fmt);
                c.packageName=getAny(row,headers,fmt,"packagename","package");
                c.packageCost=numAny(row,headers,fmt,"packagecost","monthlyamount","amount");
                c.agentName=get(row,headers,"agentname",fmt);
                c.agentPhone=get(row,headers,"agentphone",fmt);
                c.dueAmount=numAny(row,headers,fmt,"dueamount","due","pending");
                c.advanceAmount=numAny(row,headers,fmt,"advanceamount","advance");
                c.status=get(row,headers,"status",fmt);
                if(c.status.isEmpty()) c.status="Active";
                c.subscription=get(row,headers,"subscription",fmt);
                result.customers.add(c);
            }
        } catch(Exception e) {
            result.errors++;
            result.errorMessages.add(e.getMessage()==null?e.toString():e.getMessage());
        }
        return result;
    }

    private static String norm(String s){
        return s==null?"":s.replace("\u00A0"," ").trim().toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]","");
    }

    private static String get(Row row,Map<String,Integer> h,String key,DataFormatter f){
        Integer i=h.get(key);
        if(i==null) return "";
        Cell c=row.getCell(i,Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return c==null?"":f.formatCellValue(c).trim();
    }

    private static String getAny(Row row,Map<String,Integer> h,DataFormatter f,String... keys){
        for(String k:keys){
            String v=get(row,h,k,f);
            if(!v.isEmpty()) return v;
        }
        return "";
    }

    private static double numAny(Row row,Map<String,Integer> h,DataFormatter f,String... keys){
        String s=getAny(row,h,f,keys).replace(",","").replace("₹","").trim();
        if(s.isEmpty()) return 0;
        try{return Double.parseDouble(s.replaceAll("[^0-9.\\-]",""));}catch(Exception e){return 0;}
    }
}

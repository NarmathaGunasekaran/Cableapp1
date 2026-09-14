package com.cablemanager;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.*;
import java.io.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_EXCEL=1001, CREATE_EXCEL=1002, CREATE_BACKUP=1003, RESTORE_BACKUP=1004;
    private final ArrayList<Customer> customers=new ArrayList<>();
    private final ArrayList<JSONObject> payments=new ArrayList<>();
    private final ArrayList<String> billedMonths=new ArrayList<>();
    private final NumberFormat money=NumberFormat.getCurrencyInstance(new Locale("en","IN"));
    private Customer selected;
    private CustomerAdapter customerAdapter;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(9,37,58));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        load();
        showLogin();
    }

    private void setup(String title, boolean nav){
        setContentView(layoutId(title));
        TextView back=findViewById(R.id.btnBack);
        TextView more=findViewById(R.id.btnMore);
        TextView t=findViewById(R.id.txtTitle);
        if(t!=null)t.setText(title);
        if(back!=null)back.setOnClickListener(v->showDashboard());
        if(more!=null)more.setOnClickListener(v->showMenu());
        if(!nav) {
            View bottom=findViewById(R.id.bottomNav);
            if(bottom!=null) bottom.setVisibility(View.GONE);
        } else setupBottom();
    }

    private int layoutId(String title){
        switch(title){
            case "MyCable Pro": return R.layout.screen_dashboard;
            case "Customers": return R.layout.screen_customers;
            case "Customer Details": return R.layout.screen_customer_details;
            case "Add Customer": return R.layout.screen_add_customer;
            case "Edit Customer": return R.layout.screen_add_customer;
            case "Collect Payment": return R.layout.screen_collect_payment;
            case "Advance Payment": return R.layout.screen_advance_payment;
            case "Payment History": return R.layout.screen_payment_history;
            case "Billing": return R.layout.screen_billing;
            case "Reports": return R.layout.screen_reports;
            case "Day Collection Report": return R.layout.screen_day_collection_report;
            case "Search / Filter": return R.layout.screen_search_filter;
            case "Packages": return R.layout.screen_packages;
            case "Zones": return R.layout.screen_zones;
            case "Backup & Restore": return R.layout.screen_export_import;
            case "Monthly Billing": return R.layout.screen_monthly_billing;
            default: return R.layout.screen_menu_settings;
        }
    }

    private void setupBottom(){
        View v=findViewById(R.id.bottomNav);
        if(v==null)return;
        findViewById(R.id.navHome).setOnClickListener(x->showDashboard());
        findViewById(R.id.navCustomers).setOnClickListener(x->showCustomers());
        findViewById(R.id.navBilling).setOnClickListener(x->showPayments());
        findViewById(R.id.navReports).setOnClickListener(x->showReports());
    }

    private void showLogin(){
        setContentView(R.layout.activity_login);
        EditText u=findViewById(R.id.username), p=findViewById(R.id.password);
        findViewById(R.id.btnLogin).setOnClickListener(v->{
            if(p.getText().toString().trim().isEmpty() || p.getText().toString().equals("123456")){
                showDashboard();
            }else toast("Password is 123456");
        });
    }

    private void showDashboard(){
        setup("MyCable Pro",true);
        ((TextView)findViewById(R.id.statTotal)).setText("👥 Total Customers\n"+customers.size());
        ((TextView)findViewById(R.id.statActive)).setText("✓ Active\n"+countActive());
        ((TextView)findViewById(R.id.statDue)).setText("⊗ Due\n"+money.format(totalDue()));
        ((TextView)findViewById(R.id.statAdvance)).setText("₹ Advance\n"+money.format(totalAdvance()));

        findViewById(R.id.actionCustomers).setOnClickListener(v->showCustomers());
        findViewById(R.id.actionBilling).setOnClickListener(v->showPayments());
        findViewById(R.id.actionCollection).setOnClickListener(v->showCollect(false));
        findViewById(R.id.actionPackages).setOnClickListener(v->showPackages());
        findViewById(R.id.actionZones).setOnClickListener(v->showZones());
        findViewById(R.id.actionReports).setOnClickListener(v->showReports());
        findViewById(R.id.btnImportExcel).setOnClickListener(v->pickExcel());
        findViewById(R.id.btnExportExcel).setOnClickListener(v->createExport());
    }

    private void showCustomers(){
        setup("Customers",true);
        LinearLayout chips=findViewById(R.id.chips);
        chips.removeAllViews();
        String[] modes={"All","Due","Advance","Inactive"};
        for(String m:modes){
            TextView c=chip(m);
            chips.addView(c,new LinearLayout.LayoutParams(dp(74),dp(36)));
            c.setOnClickListener(v->applyCustomerMode(((TextView)v).getText().toString()));
        }

        ListView list=findViewById(R.id.customerList);
        customerAdapter=new CustomerAdapter(this);
        list.setAdapter(customerAdapter);
        customerAdapter.setAll(customers);
        updateCustomerCount();

        EditText search=findViewById(R.id.searchCustomer);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){ customerAdapter.filter(s.toString()); updateCustomerCount(); }
            public void afterTextChanged(Editable e){}
        });

        list.setOnItemClickListener((parent,view,pos,id)->{
            selected=customerAdapter.getItem(pos);
            showDetails(selected);
        });
        findViewById(R.id.btnFilter).setOnClickListener(v->showFilter());
    }

    private void applyCustomerMode(String mode){
        ArrayList<Customer> out=new ArrayList<>();
        for(Customer c:customers){
            boolean ok=mode.equals("All")
                    ||(mode.equals("Due")&&c.dueAmount>0)
                    ||(mode.equals("Advance")&&c.advanceAmount>0)
                    ||(mode.equals("Inactive")&&"Inactive".equalsIgnoreCase(c.status));
            if(ok)out.add(c);
        }
        customerAdapter.setAll(out);
        updateCustomerCount();
    }

    private void updateCustomerCount(){
        TextView t=findViewById(R.id.customerCount);
        if(t!=null && customerAdapter!=null)t.setText(customerAdapter.getCount()+" customer(s) shown");
    }

    private TextView chip(String s){
        TextView t=new TextView(this);
        t.setText(s); t.setTextSize(12); t.setTextColor(Color.rgb(30,42,55));
        t.setGravity(Gravity.CENTER); t.setBackgroundResource(R.drawable.bg_chip);
        return t;
    }

    private void showDetails(Customer c){
        if(c==null){showCustomers();return;}
        setup("Customer Details",true);
        LinearLayout box=findViewById(R.id.detailsContent);
        addCard(box,"👤  "+safe(c.name)+"\n"+safe(c.phone)+"\nBOX: "+safe(c.boxId));
        addCard(box,"📍 Zone\n"+safe(c.zone));
        addCard(box,"📦 Package\n"+safe(c.packageName)+"   •   ₹"+fmt(c.packageCost));
        addCard(box,"Status\n"+(safe(c.status).isEmpty()?"Active":c.status));
        LinearLayout bal=new LinearLayout(this); bal.setOrientation(LinearLayout.HORIZONTAL);
        bal.addView(balance("Bill",c.packageCost)); bal.addView(balance("Paid",Math.max(0,c.packageCost-c.dueAmount)));
        bal.addView(balance("Due",c.dueAmount)); bal.addView(balance("Advance",c.advanceAmount));
        box.addView(bal,marginParams(0,8));
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button collect=button("Collect",Color.rgb(0,184,135)); Button hist=button("History",Color.rgb(36,118,232)); Button edit=button("Edit",Color.rgb(118,82,232));
        actions.addView(collect,weightParams(0)); actions.addView(hist,weightParams(6)); actions.addView(edit,weightParams(6));
        box.addView(actions,marginParams(0,8));
        collect.setOnClickListener(v->showCollect(false));
        hist.setOnClickListener(v->showPaymentHistory(c));
        edit.setOnClickListener(v->showAdd(c));
        addCard(box,"📍 Address\n"+safe(c.address));
        addCard(box,"💳 Card / Smart Card\n"+safe(c.cardNo));
        addCard(box,"Agent\n"+safe(c.agentName)+"  "+safe(c.agentPhone));
        Button deactivate=button("Deactivate Customer",Color.rgb(217,54,62));
        box.addView(deactivate,marginParams(0,8));
        deactivate.setOnClickListener(v->{c.status="Inactive";save();showDetails(c);});
    }

    private void showAdd(Customer edit){
        setup(edit==null?"Add Customer":"Edit Customer",true);
        EditText name=findViewById(R.id.name), phone=findViewById(R.id.phone), box=findViewById(R.id.box),
                card=findViewById(R.id.cardNo), mso=findViewById(R.id.mso), zone=findViewById(R.id.zone),
                pkg=findViewById(R.id.packageName), cost=findViewById(R.id.packageCost), address=findViewById(R.id.address),
                agent=findViewById(R.id.agentName), agentPhone=findViewById(R.id.agentPhone);
        RadioButton active=findViewById(R.id.active), inactive=findViewById(R.id.inactive);
        active.setChecked(edit==null || !"Inactive".equalsIgnoreCase(edit.status));
        if(edit!=null){
            name.setText(edit.name); phone.setText(edit.phone); box.setText(edit.boxId); card.setText(edit.cardNo);
            mso.setText(edit.mso); zone.setText(edit.zone); pkg.setText(edit.packageName); cost.setText(fmt(edit.packageCost));
            address.setText(edit.address); agent.setText(edit.agentName); agentPhone.setText(edit.agentPhone);
            inactive.setChecked("Inactive".equalsIgnoreCase(edit.status));
        }
        findViewById(R.id.btnSaveCustomer).setOnClickListener(v->{
            String id=box.getText().toString().trim();
            if(id.isEmpty()){toast("BOX ID is required");return;}
            Customer c=edit==null?new Customer():edit;
            c.boxId=id; c.name=name.getText().toString().trim(); c.phone=phone.getText().toString().trim();
            c.cardNo=card.getText().toString().trim(); c.mso=mso.getText().toString().trim(); c.zone=zone.getText().toString().trim();
            c.packageName=pkg.getText().toString().trim(); c.packageCost=num(cost); c.address=address.getText().toString().trim();
            c.agentName=agent.getText().toString().trim(); c.agentPhone=agentPhone.getText().toString().trim();
            c.status=active.isChecked()?"Active":"Inactive";
            if(edit==null){
                if(findByBox(id)!=null){toast("BOX ID already exists");return;}
                customers.add(c);
            }
            save(); selected=c; showDetails(c);
        });
    }

    private void showCollect(boolean advanceOnly){
        if(selected==null && !customers.isEmpty())selected=customers.get(0);
        if(selected==null){showAdd(null);return;}
        Customer c=selected;
        setup(advanceOnly?"Advance Payment":"Collect Payment",true);
        LinearLayout form=findViewById(R.id.paymentForm);
        addCard(form,"👤 "+safe(c.name)+"\nBOX: "+safe(c.boxId));
        addCard(form,"Current Due     "+money.format(c.dueAmount)+"\nAdvance Balance  "+money.format(c.advanceAmount));
        TextView pt=label("Payment Type",13,true); form.addView(pt,marginParams(0,10));
        RadioGroup rg=new RadioGroup(this); rg.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton normal=new RadioButton(this); normal.setText("Normal Payment"); normal.setChecked(!advanceOnly);
        RadioButton adv=new RadioButton(this); adv.setText("Advance Payment"); adv.setChecked(advanceOnly);
        rg.addView(normal); rg.addView(adv); form.addView(rg);
        EditText amount=field("Amount Paid"); form.addView(amount,marginParams(0,8));
        TextView modeLabel=label("Payment Mode",13,true); form.addView(modeLabel,marginParams(0,8));
        Spinner mode=new Spinner(this); mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Cash","Cheque","UPI/Other"})); form.addView(mode);
        EditText notes=field("Notes (optional)"); form.addView(notes,marginParams(0,8));
        Button save=button("SAVE PAYMENT",Color.rgb(0,184,135)); form.addView(save,marginParams(0,10));
        save.setOnClickListener(v->{
            double a=num(amount);
            if(a<=0){toast("Enter a valid amount");return;}
            boolean advPay=adv.isChecked();
            if(advPay)c.advanceAmount+=a;
            else{
                double used=Math.min(a,Math.max(0,c.dueAmount));
                c.dueAmount-=used;
                double extra=a-used;
                if(extra>0)c.advanceAmount+=extra;
            }
            JSONObject p=new JSONObject();
            try{
                p.put("boxId",c.boxId); p.put("name",c.name); p.put("amount",a);
                p.put("type",advPay?"Advance":"Normal"); p.put("mode",mode.getSelectedItem().toString());
                p.put("date",date()); p.put("notes",notes.getText().toString());
                payments.add(p);
            }catch(Exception ignored){}
            save(); toast("Payment saved"); showDetails(c);
        });
    }

    private void showPaymentHistory(Customer c){
        setup("Payment History",true);
        LinearLayout box=findViewById(R.id.historyContent);
        addCard(box,"👤 "+safe(c.name)+"\nBOX: "+safe(c.boxId));
        boolean found=false;
        for(JSONObject p:payments){
            if(!p.optString("boxId").equals(c.boxId))continue;
            found=true;
            addCard(box,p.optString("date")+"   •   "+p.optString("type")+
                    "\n₹"+fmt(p.optDouble("amount"))+"   •   "+p.optString("mode")+
                    (p.optString("notes").isEmpty()?"":"\n"+p.optString("notes")));
        }
        if(!found)addCard(box,"No payments recorded yet.");
    }

    private void showPayments(){
        setup("Billing",true);
        LinearLayout box=findViewById(R.id.content);
        addCard(box,"Payment Collection\nCollect normal monthly payment or advance payment.");
        Button normal=button("NORMAL COLLECTION",Color.rgb(0,184,135));
        Button adv=button("ADVANCE PAYMENT",Color.rgb(36,118,232));
        Button monthly=button("MONTHLY BILLING",Color.rgb(118,82,232));
        box.addView(normal,marginParams(0,8)); box.addView(adv,marginParams(0,8)); box.addView(monthly,marginParams(0,8));
        addCard(box,"Total Due\n"+money.format(totalDue())+"\n\nTotal Advance\n"+money.format(totalAdvance()));
        normal.setOnClickListener(v->showCollect(false)); adv.setOnClickListener(v->showCollect(true)); monthly.setOnClickListener(v->showMonthly());
    }

    private void showReports(){
        setup("Reports",true);
        LinearLayout box=findViewById(R.id.content);
        addCard(box,"₹ "+fmt(totalPaid())+"\nCollected");
        addCard(box,"₹ "+fmt(totalDue())+"\nPending");
        addCard(box,"₹ "+fmt(totalAdvance())+"\nAdvance");
        Button day=button("DAY COLLECTION REPORT",Color.rgb(36,118,232));
        Button export=button("EXPORT EXCEL",Color.rgb(0,184,135));
        box.addView(day,marginParams(0,8)); box.addView(export,marginParams(0,8));
        day.setOnClickListener(v->showDayReport()); export.setOnClickListener(v->createExport());
    }

    private void showDayReport(){
        setup("Day Collection Report",false);
        LinearLayout box=findViewById(R.id.reportContent);
        String today=date(); double collected=0;
        for(JSONObject p:payments)if(today.equals(p.optString("date")))collected+=p.optDouble("amount");
        addCard(box,"Date: "+today);
        addCard(box,"Opening Due\n₹"+fmt(totalDue()+collected));
        addCard(box,"Collected Today\n₹"+fmt(collected));
        addCard(box,"Closing Due\n₹"+fmt(totalDue()));
        addCard(box,"Total Payments Today\n"+countPaymentsToday());
    }

    private void showFilter(){
        setup("Search / Filter",true);
        LinearLayout box=findViewById(R.id.filterContent);
        addCard(box,"Filter Customers");
        CheckBox due=new CheckBox(this); due.setText("Due Customers"); box.addView(due);
        CheckBox adv=new CheckBox(this); adv.setText("Advance Customers"); box.addView(adv);
        CheckBox inactive=new CheckBox(this); inactive.setText("Inactive Customers"); box.addView(inactive);
        Button apply=button("APPLY FILTER",Color.rgb(0,184,135)); box.addView(apply,marginParams(0,10));
        apply.setOnClickListener(v->{
            if(due.isChecked()||adv.isChecked()||inactive.isChecked()){
                ArrayList<Customer> out=new ArrayList<>();
                for(Customer c:customers){
                    if((due.isChecked()&&c.dueAmount>0)||(adv.isChecked()&&c.advanceAmount>0)||(inactive.isChecked()&&"Inactive".equalsIgnoreCase(c.status)))out.add(c);
                }
                showCustomersWithList(out);
            }else showCustomers();
        });
    }

    private void showCustomersWithList(ArrayList<Customer> list){
        showCustomers();
        customerAdapter.setAll(list);
        updateCustomerCount();
    }

    private void showPackages(){
        setup("Packages",true);
        LinearLayout box=findViewById(R.id.content);
        for(String x:new String[]{"MONTHLY 200    ₹200","MONTHLY 280    ₹280","MONTHLY 300    ₹300","BASIC    ₹150","PREMIUM    ₹500"})
            addCard(box,"●  "+x+"    ✎  🗑");
    }

    private void showZones(){
        setup("Zones",true);
        LinearLayout box=findViewById(R.id.content);
        for(String x:new String[]{"AM PERUMAL KOVIL ST","AM MUTHUMARIYAMMAN KOVIL AP","AM MAIN ROAD","AM FLAT","AM MUTAMIL NAGAR"})
            addCard(box,x+"    ✎  🗑");
    }

    private void showMenu(){
        setupMenu();
    }

    private void setupMenu(){
        setup("MyCable Pro",false);
        LinearLayout box=findViewById(R.id.content);
        String[] items={"⚙  Dashboard","♙  Customers","▣  Billing","₹  Collection","▤  Packages","📍  Zones","▥  Reports","⇄  Export / Import","📅  Monthly Billing","↪  Logout"};
        for(String x:items){
            TextView t=label(x,16,false); box.addView(t,marginParams(0,1));
            if(x.contains("Dashboard"))t.setOnClickListener(v->showDashboard());
            else if(x.contains("Customers"))t.setOnClickListener(v->showCustomers());
            else if(x.contains("Billing"))t.setOnClickListener(v->showPayments());
            else if(x.contains("Collection"))t.setOnClickListener(v->showCollect(false));
            else if(x.contains("Packages"))t.setOnClickListener(v->showPackages());
            else if(x.contains("Zones"))t.setOnClickListener(v->showZones());
            else if(x.contains("Reports"))t.setOnClickListener(v->showReports());
            else if(x.contains("Export"))t.setOnClickListener(v->showExportImport());
            else if(x.contains("Monthly"))t.setOnClickListener(v->showMonthly());
            else if(x.contains("Logout"))t.setOnClickListener(v->showLogin());
        }
    }

    private void showExportImport(){
        setup("Backup & Restore",true);
        LinearLayout box=findViewById(R.id.content);
        addCard(box,"📗 Export Data\nExport all customers and payments to Excel.");
        Button ex=button("EXPORT EXCEL",Color.rgb(36,118,232)); box.addView(ex,marginParams(0,8));
        addCard(box,"📕 Import Data\nImport customer data from Excel. Existing BOX IDs are updated.");
        Button im=button("IMPORT EXCEL",Color.rgb(118,82,232)); box.addView(im,marginParams(0,8));
        addCard(box,"💾 Backup / Restore\nCreate a complete JSON backup.");
        Button bk=button("CREATE BACKUP",Color.rgb(36,118,232));
        Button rs=button("RESTORE BACKUP",Color.DKGRAY);
        box.addView(bk,marginParams(0,8)); box.addView(rs,marginParams(0,8));
        ex.setOnClickListener(v->createExport()); im.setOnClickListener(v->pickExcel()); bk.setOnClickListener(v->createBackup()); rs.setOnClickListener(v->restoreBackup());
    }

    private void showMonthly(){
        setup("Monthly Billing",true);
        LinearLayout box=findViewById(R.id.content);
        addCard(box,"Generate monthly bills for all active customers.\nAdvance balances are used first.");
        EditText month=field("Month (e.g. September 2026)");
        month.setText(new SimpleDateFormat("MMMM yyyy",Locale.US).format(new Date()));
        box.addView(month,marginParams(0,10));
        Button gen=button("GENERATE BILLS",Color.rgb(0,184,135)); box.addView(gen,marginParams(0,10));
        gen.setOnClickListener(v->{String m=month.getText().toString().trim(); if(m.isEmpty()){toast("Enter month");return;} generateBills(m);});
    }

    private void generateBills(String month){
        if(billedMonths.contains(month)){toast("Bills already generated for "+month);return;}
        int n=0;
        for(Customer c:customers){
            if("Inactive".equalsIgnoreCase(c.status))continue;
            double bill=Math.max(0,c.packageCost);
            double use=Math.min(Math.max(0,c.advanceAmount),bill);
            c.advanceAmount-=use; bill-=use; c.dueAmount+=bill; n++;
        }
        billedMonths.add(month); save(); toast(n+" monthly bills generated"); showDashboard();
    }

    private void pickExcel(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        startActivityForResult(i,PICK_EXCEL);
    }

    private void createExport(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        i.putExtra(Intent.EXTRA_TITLE,"MyCablePro_Export.xlsx");
        startActivityForResult(i,CREATE_EXCEL);
    }

    private void createBackup(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"MyCablePro_Backup.json"); startActivityForResult(i,CREATE_BACKUP);
    }

    private void restoreBackup(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json"); startActivityForResult(i,RESTORE_BACKUP);
    }

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(result!=RESULT_OK||data==null)return;
        try{
            if(req==PICK_EXCEL){
                ExcelImporter.Result r=ExcelImporter.read(this,data.getData());
                int before=customers.size();
                for(Customer c:r.customers)upsert(c);
                save();
                if(r.customers.size()>0){
                    toast("Imported "+r.customers.size()+" customers");
                    showCustomers();
                }else{
                    toast("No customers imported");
                    showImportResult(r);
                }
                if(r.errors>0)showImportResult(r);
            }else if(req==CREATE_EXCEL)writeExcel(data.getData());
            else if(req==CREATE_BACKUP)writeBackup(data.getData());
            else if(req==RESTORE_BACKUP)readBackup(data.getData());
        }catch(Exception e){toast("Error: "+e.getMessage());}
    }

    private void showImportResult(ExcelImporter.Result r){
        StringBuilder s=new StringBuilder();
        s.append("Imported: ").append(r.customers.size()).append("\nSkipped: ").append(r.skipped).append("\nErrors: ").append(r.errors);
        for(String x:r.errorMessages)s.append("\n").append(x);
        new AlertDialog.Builder(this).setTitle("Excel Import Result").setMessage(s.toString()).setPositiveButton("OK",null).show();
    }

    private void upsert(Customer c){
        Customer old=findByBox(c.boxId);
        if(old==null)customers.add(c);
        else{
            int idx=customers.indexOf(old);
            customers.set(idx,c);
        }
    }

    private Customer findByBox(String id){
        for(Customer c:customers)if(c.boxId.equalsIgnoreCase(id))return c;
        return null;
    }

    private void writeExcel(Uri u)throws Exception{
        try(OutputStream out=getContentResolver().openOutputStream(u); Workbook wb=new XSSFWorkbook()){
            Sheet s=wb.createSheet("Customers");
            String[] h={"BOX ID","CUSTOMER NAME","PHONE NO","ADDRESS","ZONE","PACKAGE NAME","PACKAGE COST","DUE AMOUNT","ADVANCE AMOUNT","STATUS","CARD NO","MSO","AGENT NAME","AGENT PHONE","SUBSCRIPTION"};
            Row r=s.createRow(0); for(int i=0;i<h.length;i++)r.createCell(i).setCellValue(h[i]);
            int row=1;
            for(Customer c:customers){
                Row x=s.createRow(row++);
                String[] v={c.boxId,c.name,c.phone,c.address,c.zone,c.packageName};
                for(int i=0;i<v.length;i++)x.createCell(i).setCellValue(v[i]);
                x.createCell(6).setCellValue(c.packageCost); x.createCell(7).setCellValue(c.dueAmount);
                x.createCell(8).setCellValue(c.advanceAmount); x.createCell(9).setCellValue(c.status);
                x.createCell(10).setCellValue(c.cardNo); x.createCell(11).setCellValue(c.mso);
                x.createCell(12).setCellValue(c.agentName); x.createCell(13).setCellValue(c.agentPhone);
                x.createCell(14).setCellValue(c.subscription);
            }
            wb.write(out);
        }
        toast("Excel exported: "+customers.size()+" customers");
    }

    private void writeBackup(Uri u)throws Exception{
        JSONObject root=new JSONObject(); JSONArray a=new JSONArray(); JSONArray p=new JSONArray();
        for(Customer c:customers)a.put(c.toJson()); for(JSONObject x:payments)p.put(x);
        root.put("customers",a); root.put("payments",p); root.put("billedMonths",new JSONArray(billedMonths));
        try(OutputStream out=getContentResolver().openOutputStream(u)){out.write(root.toString(2).getBytes("UTF-8"));}
        toast("Backup created");
    }

    private void readBackup(Uri u)throws Exception{
        StringBuilder s=new StringBuilder();
        try(InputStream in=getContentResolver().openInputStream(u); BufferedReader r=new BufferedReader(new InputStreamReader(in))){
            String z; while((z=r.readLine())!=null)s.append(z);
        }
        JSONObject o=new JSONObject(s.toString()); customers.clear(); payments.clear(); billedMonths.clear();
        JSONArray a=o.optJSONArray("customers"); if(a!=null)for(int i=0;i<a.length();i++)customers.add(Customer.fromJson(a.getJSONObject(i)));
        JSONArray p=o.optJSONArray("payments"); if(p!=null)for(int i=0;i<p.length();i++)payments.add(p.getJSONObject(i));
        JSONArray bm=o.optJSONArray("billedMonths"); if(bm!=null)for(int i=0;i<bm.length();i++)billedMonths.add(bm.getString(i));
        save(); toast("Backup restored: "+customers.size()+" customers"); showDashboard();
    }

    private void load(){
        customers.clear(); payments.clear(); billedMonths.clear();
        try{
            JSONArray a=new JSONArray(getPreferences(MODE_PRIVATE).getString("customers","[]"));
            for(int i=0;i<a.length();i++)customers.add(Customer.fromJson(a.getJSONObject(i)));
        }catch(Exception ignored){}
        try{
            JSONArray a=new JSONArray(getPreferences(MODE_PRIVATE).getString("payments","[]"));
            for(int i=0;i<a.length();i++)payments.add(a.getJSONObject(i));
        }catch(Exception ignored){}
        try{
            JSONArray a=new JSONArray(getPreferences(MODE_PRIVATE).getString("billed","[]"));
            for(int i=0;i<a.length();i++)billedMonths.add(a.getString(i));
        }catch(Exception ignored){}
    }

    private void save(){
        try{
            JSONArray a=new JSONArray(),p=new JSONArray();
            for(Customer c:customers)a.put(c.toJson()); for(JSONObject x:payments)p.put(x);
            getPreferences(MODE_PRIVATE).edit().putString("customers",a.toString()).putString("payments",p.toString())
                    .putString("billed",new JSONArray(billedMonths).toString()).apply();
        }catch(Exception ignored){}
    }

    private int countActive(){int n=0;for(Customer c:customers)if(!"Inactive".equalsIgnoreCase(c.status))n++;return n;}
    private int countPaymentsToday(){int n=0;for(JSONObject p:payments)if(date().equals(p.optString("date")))n++;return n;}
    private double totalDue(){double x=0;for(Customer c:customers)x+=Math.max(0,c.dueAmount);return x;}
    private double totalAdvance(){double x=0;for(Customer c:customers)x+=Math.max(0,c.advanceAmount);return x;}
    private double totalPaid(){double x=0;for(JSONObject p:payments)x+=p.optDouble("amount");return x;}
    private String date(){return new SimpleDateFormat("dd-MM-yyyy",Locale.US).format(new Date());}
    private String fmt(double x){return String.format(Locale.US,"%.0f",x);}
    private double num(EditText e){try{return Double.parseDouble(e.getText().toString().replace(",","").replace("₹","").trim());}catch(Exception x){return 0;}}
    private String safe(String s){return s==null?"":s;}

    private TextView label(String s,int size,boolean bold){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.rgb(30,42,55));
        t.setTypeface(null,bold?1:0); t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(dp(10),dp(10),dp(10),dp(10));
        return t;
    }

    private EditText field(String hint){
        EditText e=new EditText(this); e.setHint(hint); e.setTextSize(14); e.setSingleLine(true);
        e.setBackgroundResource(R.drawable.bg_input); e.setPadding(dp(12),0,dp(12),0); return e;
    }

    private Button button(String text,int color){
        Button b=new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(13); b.setAllCaps(false);
        b.setBackgroundColor(color); return b;
    }

    private void addCard(LinearLayout parent,String text){
        TextView t=label(text,14,false); t.setBackgroundResource(R.drawable.bg_card);
        parent.addView(t,marginParams(0,7));
    }

    private TextView balance(String k,double v){
        TextView t=label(k+"\n₹"+fmt(v),11,true); t.setGravity(Gravity.CENTER); t.setBackgroundResource(R.drawable.bg_card);
        t.setLayoutParams(weightParams(0)); return t;
    }

    private LinearLayout.LayoutParams marginParams(int top,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(top),0,dp(bottom)); return p;
    }

    private LinearLayout.LayoutParams weightParams(int margin){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(70),1);
        p.setMargins(dp(margin),0,0,0); return p;
    }

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}

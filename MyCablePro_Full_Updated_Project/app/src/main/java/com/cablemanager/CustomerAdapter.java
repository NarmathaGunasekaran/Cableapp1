package com.cablemanager;

import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

public class CustomerAdapter extends ArrayAdapter<Customer> {
    private final List<Customer> all=new ArrayList<>();

    public CustomerAdapter(Context c){ super(c,0,new ArrayList<>()); }

    public void setAll(List<Customer> list){
        all.clear();
        if(list!=null) all.addAll(list);
        filter("");
    }

    public void filter(String q){
        String x=q==null?"":q.trim().toLowerCase(Locale.US);
        clear();
        for(Customer c:all){
            String hay=(c.boxId+" "+c.name+" "+c.phone+" "+c.address+" "+c.zone+" "+c.searchCode+" "+c.cardNo).toLowerCase(Locale.US);
            if(x.isEmpty() || hay.contains(x)) add(c);
        }
        notifyDataSetChanged();
    }

    public List<Customer> getAll(){ return new ArrayList<>(all); }

    @Override public View getView(int position,View convert,ViewGroup parent){
        View v=convert;
        if(v==null) v=LayoutInflater.from(getContext()).inflate(R.layout.item_customer,parent,false);
        Customer c=getItem(position);

        TextView avatar=v.findViewById(R.id.avatar);
        TextView name=v.findViewById(R.id.name);
        TextView box=v.findViewById(R.id.box);
        TextView details=v.findViewById(R.id.details);
        TextView bill=v.findViewById(R.id.bill);

        String n=c.name==null||c.name.trim().isEmpty()?"Unnamed customer":c.name.trim();
        avatar.setText(n.substring(0,1).toUpperCase(Locale.US));
        name.setText(n);
        box.setText("BOX: "+safe(c.boxId)+"   •   "+(safe(c.phone).isEmpty()?"No phone":c.phone));
        details.setText((safe(c.zone).isEmpty()?"No zone":c.zone)+"   •   "+
                (safe(c.packageName).isEmpty()?"No package":c.packageName));
        if(c.dueAmount>0){
            bill.setText("₹"+fmt(c.dueAmount)+"\nDUE");
            bill.setTextColor(Color.rgb(210,45,55));
        }else if(c.advanceAmount>0){
            bill.setText("₹"+fmt(c.advanceAmount)+"\nADVANCE");
            bill.setTextColor(Color.rgb(190,125,0));
        }else{
            bill.setText("₹0\nOK");
            bill.setTextColor(Color.rgb(0,145,105));
        }
        return v;
    }

    private String safe(String s){return s==null?"":s;}
    private String fmt(double x){return String.format(Locale.US,"%.0f",x);}
}

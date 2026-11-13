import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app/app.component';
import { routes } from './app/app.routes';
import { authInterceptor } from './app/auth/auth.service';
/** Register the functional bearer interceptor using its matching standalone provider. */
bootstrapApplication(AppComponent,{providers:[provideRouter(routes),provideHttpClient(withInterceptors([authInterceptor]))]}).catch(error=>console.error('Unable to start Nova Portal',error));

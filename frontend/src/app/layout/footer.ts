import { Component } from '@angular/core';

@Component({
  selector: 'app-footer',
  template: `
    <footer class="border-top bg-white py-3 mt-auto">
      <div class="container-xl d-flex flex-column flex-sm-row justify-content-between gap-1 small text-secondary">
        <span>SmartRecruit AI — mini-projet « IA for Software Engineering »</span>
        <span>Angular · Spring Boot · MySQL</span>
      </div>
    </footer>
  `,
})
export class Footer {}
